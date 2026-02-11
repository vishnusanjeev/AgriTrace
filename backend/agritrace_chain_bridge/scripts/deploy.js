const fs = require('fs');
const path = require('path');
const hre = require('hardhat');
const { ethers } = hre;

async function main() {
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
  console.log('Deployed AgriTraceRegistry to:', contract.address);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
