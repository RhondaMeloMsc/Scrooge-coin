import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

    /**
     * The current pool of unspent transaction outputs.
     */
    private UTXOPool internalPool;

    /**
     * Creates a public ledger whose current UTXOPool
     * (collection of unspent transaction outputs) is utxoPool.
     * This should make a defensive copy of utxoPool by using
     * the UTXOPool(UTXOPool uPool) constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // Defensive copy
        this.internalPool = new UTXOPool(utxoPool);
    }

    /**
     * Returns true if
     * (1) all outputs claimed by tx are in the current UTXO pool
     * (2) the signatures on each input of tx are valid
     * (3) no UTXO is claimed multiple times by tx
     * (4) all of tx's output values are non-negative
     * (5) the sum of tx's input values is greater than or equal
     * to the sum of its output values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null) {
            return false;
        }

        HashSet<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        // Iterate over inputs to check rules (1), (2), (3), and part of (5)
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            if (input == null) {
                return false;
            }

            // Create the UTXO this input is claiming
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) Check if the claimed UTXO is in the pool
            if (!this.internalPool.contains(utxo)) {
                return false;
            }

            // (3) Check if this UTXO has already been claimed in this same transaction
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            // Get the corresponding output to verify the signature and get the value
            Transaction.Output correspondingOutput = this.internalPool.getTxOutput(utxo);
            if (correspondingOutput == null) {
                return false; // Should not happen if .contains() is true, but good to check
            }

            // (2) Check the signature
            PublicKey pubKey = correspondingOutput.address;
            byte[] message = tx.getRawDataToSign(i);
            byte[] signature = input.signature;
            if (!Crypto.verifySignature(pubKey, message, signature)) {
                return false;
            }

            // (5) Add to the sum of input values
            inputSum += correspondingOutput.value;
        }

        // Iterate over outputs to check rules (4) and part of (5)
        for (Transaction.Output output : tx.getOutputs()) {
            if (output == null) {
                return false;
            }
            
            // (4) Check for non-negative output values
            if (output.value < 0) {
                return false;
            }
            // (5) Add to the sum of output values
            outputSum += output.value;
        }

        // (5) Check if input sum is greater than or equal to output sum
        if (inputSum < outputSum) {
            return false;
        }

        // If all checks pass
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of
     * proposed transactions, checking each transaction for
     * correctness, returning a mutually valid array of accepted
     * transactions, and updating the current UTXO pool as
     * appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) {
            return new Transaction[0];
        }

        List<Transaction> acceptedTxs = new ArrayList<>();
        Set<Transaction> unprocessedTxs = new HashSet<>(Arrays.asList(possibleTxs));

        boolean progressMade = true;
        // Keep iterating as long as we can add at least one transaction
        // from the unprocessed set. This handles dependencies.
        while (progressMade) {
            progressMade = false;
            Set<Transaction> processedInThisPass = new HashSet<>();

            for (Transaction tx : unprocessedTxs) {
                if (isValidTx(tx)) {
                    // Transaction is valid, accept it
                    progressMade = true;
                    acceptedTxs.add(tx);
                    processedInThisPass.add(tx);

                    // Update the internal UTXO pool
                    // 1. Remove spent UTXOs (inputs)
                    for (Transaction.Input input : tx.getInputs()) {
                        UTXO spentUTXO = new UTXO(input.prevTxHash, input.outputIndex);
                        this.internalPool.removeUTXO(spentUTXO);
                    }

                    // 2. Add new UTXOs (outputs)
                    byte[] txHash = tx.getHash();
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        Transaction.Output newOutput = tx.getOutput(i);
                        UTXO newUTXO = new UTXO(txHash, i);
                        this.internalPool.addUTXO(newUTXO, newOutput);
                    }
                }
            }
            // Remove all processed transactions from the main set
            unprocessedTxs.removeAll(processedInThisPass);
        }

        // Convert the list of accepted transactions to an array
        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }
}