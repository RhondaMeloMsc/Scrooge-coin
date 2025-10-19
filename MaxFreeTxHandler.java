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
                        this