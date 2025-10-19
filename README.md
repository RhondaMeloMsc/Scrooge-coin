# ScroogeCoin Transaction Handler

This project is a Java-based implementation of a centralized transaction processor for a simplified cryptocurrency, "ScroogeCoin," as described in the Princeton/Coursera cryptocurrency course.

The core of the project is to build a transaction handler (`TxHandler`) that validates transactions and processes them to build a valid ledger, correctly handling the UTXO (Unspent Transaction Output) model and preventing double-spends.

---

## Core Components

This repository contains two main implementations of the transaction processor.

### 1. `TxHandler.java`

This is the primary component of the assignment. The `TxHandler` class:
* Maintains a local copy of the current UTXO pool (the ledger of spendable coins).
* Implements the `isValidTx()` method to validate a single transaction based on 5 critical rules.
* Implements the `handleTxs()` method to process an array of new transactions, find a *mutually valid* set, and update the UTXO pool.

#### `isValidTx()` Validation Rules

A transaction is considered valid **if and only if**:
1.  All outputs claimed by the transaction (its inputs) are in the current UTXO pool.
2.  The signatures on each input are valid (i.e., signed by the correct owner).
3.  No UTXO is claimed multiple times within the same transaction.
4.  All of the transaction's output values are non-negative.
5.  The sum of the transaction's input values is greater than or equal to the sum of its output values (the difference is the transaction fee).

### 2. `MaxFeeTxHandler.java` (Extra Credit)

This is an advanced, optional component. Unlike the standard `TxHandler` which finds a *maximal* set of transactions (a "good enough" set), the `MaxFeeTxHandler` is designed to find the one specific, mutually valid set of transactions that provides the **maximum total transaction fees** to the central authority (Scrooge).

To solve this, it uses a **backtracking (recursive) algorithm** to explore all possible valid combinations of transactions and find the set with the highest fee.

---

## Key Concepts Implemented

* **UTXO (Unspent Transaction Output) Model:** The core accounting model used by Bitcoin, where the ledger consists of a set of spendable coins rather than account balances.
* **Transaction Validation:** Implementing the fundamental security checks required for a cryptocurrency.
* **Double-Spend Prevention:** Ensuring that the same "coin" (UTXO) cannot be spent more than once.
* **Greedy vs. Optimal Algorithms:**
    * `TxHandler` uses a simple, fast **greedy algorithm** to build a valid block.
    * `MaxFeeTxHandler` uses a more complex **backtracking algorithm** to find the optimal solution, demonstrating a classic computer science problem (a variation of the knapsack problem).
