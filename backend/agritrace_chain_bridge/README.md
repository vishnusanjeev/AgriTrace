# AgriTrace Chain Bridge (Local)

1) Install deps

```
npm install
```

2) Start Hardhat node

```
npx hardhat node
```

3) Deploy contract

```
npx hardhat run scripts/deploy.js --network localhost
```

4) Create .env from .env.example and set:
- PRIVATE_KEY (one of Hardhat accounts)
- CONTRACT_ADDRESS (from deployments/local.json)

5) Start bridge server

```
node src/server.js
```
