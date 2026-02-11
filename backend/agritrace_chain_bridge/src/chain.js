const { ethers } = require('ethers');
const abi = require('./abi/AgriTraceRegistry.json');

function getConfig() {
  const rpcUrl = process.env.RPC_URL || 'http://127.0.0.1:8545';
  const privateKey = process.env.PRIVATE_KEY || '';
  const contractAddress = process.env.CONTRACT_ADDRESS || '';
  return { rpcUrl, privateKey, contractAddress };
}

function getContract() {
  const { rpcUrl, privateKey, contractAddress } = getConfig();
  if (!privateKey || !contractAddress) {
    throw new Error('Bridge not configured');
  }
  const provider = new ethers.providers.JsonRpcProvider(rpcUrl);
  const wallet = new ethers.Wallet(privateKey, provider);
  const contract = new ethers.Contract(contractAddress, abi, wallet);
  return { contract, provider, contractAddress };
}

function normalizeHash(hashHex) {
  const h = String(hashHex || '').toLowerCase().replace(/^0x/, '');
  if (h.length !== 64) {
    throw new Error('Invalid hash length');
  }
  return '0x' + h;
}

async function recordBatchCreated(batchCode, hashHex) {
  const { contract, provider, contractAddress } = getContract();
  const payloadHash = normalizeHash(hashHex);

  const tx = await contract.recordBatchCreated(batchCode, payloadHash);
  const receipt = await tx.wait(1);
  const net = await provider.getNetwork();

  return {
    tx_hash: tx.hash,
    block_number: receipt.blockNumber,
    chain_id: String(net.chainId),
    contract_address: contractAddress,
    status: 'CONFIRMED'
  };
}

async function getBatchHash(batchCode) {
  const { contract, provider, contractAddress } = getContract();
  const hash = await contract.getBatchHash(batchCode);
  const net = await provider.getNetwork();

  return {
    hash_hex: hash,
    chain_id: String(net.chainId),
    contract_address: contractAddress
  };
}

async function getTxReceipt(txHash) {
  const { provider } = getContract();
  const receipt = await provider.getTransactionReceipt(txHash);
  if (!receipt) {
    throw new Error('Transaction not found');
  }
  const block = await provider.getBlock(receipt.blockNumber);
  return {
    tx_hash: txHash,
    block_number: receipt.blockNumber,
    block_hash: receipt.blockHash,
    timestamp: block?.timestamp || null
  };
}

module.exports = {
  recordBatchCreated,
  getBatchHash,
  getTxReceipt
};
