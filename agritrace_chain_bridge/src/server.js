require('dotenv').config();
const express = require('express');
const { ethers } = require('ethers');
const { recordBatchCreated, getBatchHash, getTxReceipt } = require('./chain');

const app = express();
app.use(express.json({ limit: '256kb' }));

app.get('/health', (req, res) => {
  res.json({ ok: true });
});

app.get('/bridge/health', async (req, res) => {
  const rpcUrl = process.env.RPC_URL || 'http://127.0.0.1:8545';
  const contractAddress = process.env.CONTRACT_ADDRESS || '';
  if (!contractAddress) {
    return res.status(500).json({ ok: false, error: 'CONTRACT_ADDRESS not set' });
  }

  try {
    const provider = new ethers.providers.JsonRpcProvider(rpcUrl);
    const block = await provider.getBlockNumber();
    const code = await provider.getCode(contractAddress);
    if (!code || code === '0x') {
      return res.status(500).json({ ok: false, error: 'Contract bytecode not found' });
    }
    const net = await provider.getNetwork();
    res.json({ ok: true, chain_id: String(net.chainId), latest_block: block, contract_address: contractAddress });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message || 'Bridge error' });
  }
});

app.post('/bridge/batchCreated', async (req, res) => {
  const { batch_id, hash_hex } = req.body || {};
  if (!batch_id || !hash_hex) {
    return res.status(400).json({ ok: false, error: 'batch_id and hash_hex required' });
  }

  try {
    const data = await recordBatchCreated(String(batch_id), String(hash_hex));
    res.json({ ok: true, ...data });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message || 'Bridge error' });
  }
});

app.post('/chain/batchCreated', async (req, res) => {
  const { batch_code, hash_hex } = req.body || {};
  if (!batch_code || !hash_hex) {
    return res.status(400).json({ error: 'batch_code and hash_hex required' });
  }

  try {
    const data = await recordBatchCreated(String(batch_code), String(hash_hex));
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: err.message || 'Bridge error' });
  }
});

app.get('/chain/batch/:batchCode', async (req, res) => {
  const batchCode = req.params.batchCode || '';
  if (!batchCode) return res.status(400).json({ error: 'batchCode required' });

  try {
    const data = await getBatchHash(batchCode);
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: err.message || 'Bridge error' });
  }
});

app.get('/bridge/tx/:hash', async (req, res) => {
  const hash = req.params.hash || '';
  if (!hash) return res.status(400).json({ ok: false, error: 'tx hash required' });

  try {
    const data = await getTxReceipt(hash);
    res.json({ ok: true, ...data });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message || 'Bridge error' });
  }
});

const port = process.env.BRIDGE_PORT ? parseInt(process.env.BRIDGE_PORT, 10) : 5055;
app.listen(port, () => {
  console.log(`AgriTrace bridge listening on ${port}`);
});
