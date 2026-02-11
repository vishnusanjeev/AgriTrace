const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { ethers } = require('ethers');

const rpcUrl = process.env.RPC_URL || 'http://127.0.0.1:8545';
const deploymentsPath = path.join(__dirname, '..', 'deployments', 'local.json');

async function waitForRpc(maxTries = 40) {
  const provider = new ethers.providers.JsonRpcProvider(rpcUrl);
  for (let i = 0; i < maxTries; i += 1) {
    try {
      const block = await provider.getBlockNumber();
      console.log(`[bridge] RPC ok at ${rpcUrl} (latestBlock=${block})`);
      return provider;
    } catch (_) {
      await new Promise((r) => setTimeout(r, 1500));
    }
  }
  throw new Error('RPC not reachable at ' + rpcUrl);
}

function readDeploymentAddress() {
  if (!fs.existsSync(deploymentsPath)) return null;
  const data = JSON.parse(fs.readFileSync(deploymentsPath, 'utf-8'));
  return data.address || data.contractAddress || null;
}

async function hasBytecode(provider, address) {
  if (!address) return false;
  const code = await provider.getCode(address);
  return code && code !== '0x';
}

function deployContract() {
  console.log('[bridge] Deploying contract...');
  execSync('npx hardhat compile', { stdio: 'inherit' });
  execSync('npx hardhat run scripts/deploy.js --network localhost', { stdio: 'inherit' });
}

async function ensureContract(provider) {
  let address = readDeploymentAddress();
  if (address && await hasBytecode(provider, address)) {
    console.log(`[bridge] Using existing contract ${address}`);
    return address;
  }

  if (address) {
    console.log(`[bridge] No bytecode at ${address}, redeploying...`);
  } else {
    console.log('[bridge] No deployment found, deploying...');
  }

  deployContract();
  address = readDeploymentAddress();
  if (!address) {
    throw new Error('Deployment failed: address not found');
  }
  if (!await hasBytecode(provider, address)) {
    throw new Error('Deployment failed: no bytecode at ' + address);
  }
  console.log(`[bridge] Deployed contract ${address}`);
  return address;
}

async function main() {
  const provider = await waitForRpc();
  const address = await ensureContract(provider);
  process.env.CONTRACT_ADDRESS = address;
  require('../src/server');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
