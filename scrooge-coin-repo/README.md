# 🪙 Scrooge Coin (SCG) — Cleaned & Structured Repository

This repository was reorganized from your upload on 2025-11-05 17:58 to a standard, interview‑ready GitHub layout.

## What I did
- Collected all **Solidity** contracts into `contracts/contracts/`
- Normalized **Hardhat** config/scripts/tests into `contracts/`
- Detected any **frontend** apps and moved them into `dapp/`
- Preserved **all original files** under `original_upload_backup/`

## Quick Start (Contracts)
```bash
cd contracts
npm install
cp .env.example .env
# edit: SEPOLIA_RPC_URL, PRIVATE_KEY, TOKEN_NAME, TOKEN_SYMBOL, INITIAL_SUPPLY
npm run build
npm test
# deploy (optional):
npm run deploy:sepolia
```

## Quick Start (dApp) — if present
```bash
cd dapp
npm install
npm run dev
```

> Educational/testnet only — not audited. Never commit private keys.
