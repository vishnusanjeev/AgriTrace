const hre = require('hardhat');
const { ethers } = hre;
const fs = require('fs');
const path = require('path');

const RPC_URL = process.env.RPC_URL || 'http://127.0.0.1:8545';
const deploymentsPath = path.join(__dirname, '..', 'deployments', 'local.json');

async function task1() {
  const provider = new ethers.providers.JsonRpcProvider(RPC_URL);
  const net = await provider.getNetwork();
  const block = await provider.getBlockNumber();
  console.log(`[TASK1] rpc ok chainId=${net.chainId} latestBlock=${block}`);
  return provider;
}

function readDeploymentAddress() {
  if (!fs.existsSync(deploymentsPath)) return null;
  const data = JSON.parse(fs.readFileSync(deploymentsPath, 'utf-8'));
  return data.address || data.contractAddress || null;
}

async function ensureContract(provider) {
  let address = readDeploymentAddress();
  if (address) {
    const code = await provider.getCode(address);
    if (code && code !== '0x') {
      console.log(`[TASK2] contract ok address=${address}`);
      return address;
    }
  }

  const Factory = await ethers.getContractFactory('AgriTraceRegistry');
  const contract = await Factory.deploy();
  await contract.deployed();

  const outDir = path.join(__dirname, '..', 'deployments');
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }
  const data = {
    address: contract.address,
    chainId: (await ethers.provider.getNetwork()).chainId
  };
  fs.writeFileSync(path.join(outDir, 'local.json'), JSON.stringify(data, null, 2));

  console.log(`[TASK2] deployed address=${contract.address}`);
  return contract.address;
}

async function task3(provider, address) {
  const abi = require('../src/abi/AgriTraceRegistry.json');
  const signer = process.env.PRIVATE_KEY
    ? new ethers.Wallet(process.env.PRIVATE_KEY, provider)
    : provider.getSigner(0);
  const contract = new ethers.Contract(address, abi, signer);

  const batchId = 1;
  const hashHex = '0x' + 'a'.repeat(64);

  const tx = await contract.recordBatchCreated(String(batchId), hashHex);
  await tx.wait(1);
  const onchain = await contract.getBatchHash(String(batchId));

  if (onchain.toLowerCase() !== hashHex.toLowerCase()) {
    throw new Error('Hash mismatch');
  }

  console.log(`[TASK3] txHash=${tx.hash} hash=${onchain}`);
}

async function main() {
  const provider = await task1();
  const address = await ensureContract(provider);
  await task3(provider, address);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
