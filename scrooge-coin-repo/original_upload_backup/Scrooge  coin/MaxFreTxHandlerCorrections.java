import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaxFeeTxHandler {

    private UTXOPool internalPool;

    /**
     * Creates a public ledger whose current UTXOPool
     * (collection of unspent transaction outputs) is utxoPool.
     * This should make a defensive copy of utxoPool by using
     * the UTXOPool(UTXOPool uPool) constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.internalPool = new UTXOPool(utxoPool);
    }

    /**
     * Private helper method to check if a transaction is valid given a
     * *specific* UTXOPool. This is different from TxHandler's isValidTx,
     * which uses the internal member pool.
     */
    private boolean isValidTx(Transaction tx, UTXOPool pool) {
        if (tx == null) {
            return false;
        }

        HashSet<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            if (input == null) return false;

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            
            // (1) Check if the claimed UTXO is in the given pool
            if (!pool.contains(utxo)) {
                return false;
            }

            // (3) Check for double-spend within the same transaction
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            Transaction.Output correspondingOutput = pool.getTxOutput(utxo);
            if (correspondingOutput == null) return false;

            // (2) Check the signature
            PublicKey pubKey = correspondingOutput.address;
            byte[] message = tx.getRawDataToSign(i);
            byte[] signature = input.signature;
            if (!Crypto.verifySignature(pubKey, message, signature)) {
                return false;
            }

            // (5) Add to input sum
            inputSum += correspondingOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            if (output == null) return false;
            
            // (4) Check for non-negative output values
            if (output.value < 0) {
                return false;
            }
            // (5) Add to output sum
            outputSum += output.value;
        }

        // (5) Check if input sum is >= output sum
        return inputSum >= outputSum;
    }
    
    /**
     * Private helper to calculate the fee of a transaction given a specific pool.
     */
    private double calculateFee(Transaction tx, UTXOPool pool) {
        double inputSum = 0;
        double outputSum = 0;

        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            // We assume tx is valid, so pool.contains(utxo) is true
            if (pool.contains(utxo)) {
                inputSum += pool.getTxOutput(utxo).value;
            }
        }

        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }
        
        return inputSum - outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * finding a mutually valid set of accepted transactions that maximizes the
     * total transaction Rfee, and updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // Find the optimal set of transactions using backtracking
        Set<Transaction> optimalSet = findMaxFeeSet(this.internalPool, new HashSet<>(Arrays.asList(possibleTxs)));
        
        // Now, update the internal UTXO pool based on this optimal set.
        // We can re-use the iterative logic from TxHandler to process the
        // set in a valid order (handling dependencies).
        
        List<Transaction> txsToProcess = new ArrayList<>(optimalSet);
        Set<Transaction> acceptedTxs = new HashSet<>();
        
        boolean progressMade = true;
        while (progressMade) {
            progressMade = false;
            List<Transaction> processedInThisPass = new ArrayList<>();

            for (Transaction tx : txsToProcess) {
                // We use the *internal* pool for this check
                if (isValidTx(tx, this.internalPool)) {
                    progressMade = true;
                    acceptedTxs.add(tx);
                    processedInThisPass.add(tx);

                    // 1. Remove spent UTXOs
                    for (Transaction.Input input : tx.getInputs()) {
                        UTXO spentUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                        this.internalPool.removeUTXO(spentUTXO);
                    }
                    // 2. Add new UTXOs
                    byte[] txHash = tx.getHash();
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        UTXO newUTXO = new UTXO(txHash, i);
                        this.internalPool.addUTXO(newUTXO, tx.getOutput(i));
                    }
                }
            }
            txsToProcess.removeAll(processedInThisPass);
        }

        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }
    
    /**
     * Recursive helper to find the max-fee set.
     * This is a 0/1 knapsack-style backtracking problem.
     * @param pool The current state of the UTXO pool for this branch.
     * @param remainingTxs The set of transactions left to consider.
     * @return The set of transactions that yields the maximum fee from this state.
     */
    private Set<Transaction> findMaxFeeSet(UTXOPool pool, Set<Transaction> remainingTxs) {
        // Base case: no more transactions to consider
        if (remainingTxs.isEmpty()) {
            return new HashSet<>();
        }
        
        // Convert to list to pick one
        List<Transaction> txsList = new ArrayList<>(remainingTxs);
        Transaction tx = txsList.remove(0);
        Set<Transaction> remaining = new HashSet<>(txsList);

        // --- Branch 1: EXCLUDE this transaction ---
        // We simply find the best set from the remaining transactions.
        Set<Transaction> setExclude = findMaxFeeSet(pool, remaining);
        double feeExclude = getTotalFee(setExclude, pool);

        // --- Branch 2: INCLUDE this transaction ---
        // This is only possible if the tx is valid in the current pool.
        if (!isValidTx(tx, pool)) {
            // Can't include, so the "exclude" branch is our only option.
            return setExclude;
        }

        // It is valid, so we can *try* to include it.
        double feeInclude = calculateFee(tx, pool);
        
        // Create the new pool state *after* this tx is processed
        UTXOPool nextPool = new UTXOPool(pool);
        // Remove spent UTXOs
        for (Transaction.Input input : tx.getInputs()) {
            UTXO spentUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            nextPool.removeUTXO(spentUTXO);
        }
        // Add new UTXOs
        byte[] txHash = tx.getHash();
        for (int i = 0; i < tx.numOutputs(); i++) {
            UTXO newUTXO = new UTXO(txHash, i);
            nextPool.addUTXO(newUTXO, tx.getOutput(i));
        }
        
        // Now, we must remove all conflicting transactions from the remaining set
        Set<Transaction> remainingAfterInclude = new HashSet<>();
        Set<UTXO> inputsSpentByTx = new HashSet<>();
        for (Transaction.Input input : tx.getInputs()) {
            inputsSpentByTx.add(new UTXO(input.prevTxHash, input.outputIndex));
        }

        for (Transaction otherTx : remaining) {
            boolean hasConflict = false;
            for (Transaction.Input otherInput : otherTx.getInputs()) {
                UTXO otherUTXO = new UTXO(otherInput.prevTxHash, otherInput.outputIndex);
                if (inputsSpentByTx.contains(otherUTXO)) {
                    hasConflict = true;
                    break;
                }
            }
            if (!hasConflict) {
                remainingAfterInclude.add(otherTx);
            }
        }
        
        // Recurse on the remaining non-conflicting transactions
        Set<Transaction> setInclude = findMaxFeeSet(nextPool, remainingAfterInclude);
        
        // Add our current tx to the result of the recursive call
        feeInclude += getTotalFee(setInclude, nextPool);
        setInclude.add(tx); // Add the current transaction to the set

        // --- Compare and return the best branch ---
        if (feeInclude > feeExclude) {
            return setInclude;
        } else {
            return setExclude;
        }
    }
    
    /**
     * Helper to calculate the total fee of a *set* of transactions
     * against a *base* pool (to handle dependencies).
     */
    private double getTotalFee(Set<Transaction> txs, UTXOPool pool) {
        if (txs.isEmpty()) {
            return 0;
        }
        
        double totalFee = 0;
        List<Transaction> txsToProcess = new ArrayList<>(txs);
        UTXOPool tempPool = new UTXOPool(pool);
        
        boolean progressMade = true;
        while (progressMade) {
            progressMade = false;
            List<Transaction> processedThisPass = new ArrayList<>();
            for (Transaction tx : txsToProcess) {
                if (isValidTx(tx, tempPool)) {
                    progressMade = true;
                    processedThisPass.add(tx);
                    totalFee += calculateFee(tx, tempPool);
                    
                    // Update tempPool
                    for (Transaction.Input input : tx.getInputs()) {
                        tempPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
                    }
                    byte[] txHash = tx.getHash();
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        tempPool.addUTXO(new UTXO(txHash, i), tx.getOutput(i));
                    }
                }
            }
            txsToProcess.removeAll(processedThisPass);
        }
        return totalFee;
    }
}