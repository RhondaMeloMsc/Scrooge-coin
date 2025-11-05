import { ethers } from "hardhat";
import "dotenv/config";

async function main() {
  const name = process.env.TOKEN_NAME || "Scrooge Coin";
  const symbol = process.env.TOKEN_SYMBOL || "SCG";
  const initial = BigInt(process.env.INITIAL_SUPPLY || "1000000");

  const [deployer] = await ethers.getSigners();
  console.log("Deployer:", deployer.address, "initial supply:", initial.toString());

  const Token = await ethers.getContractFactory("ScroogeCoin");
  const token = await Token.deploy(name, symbol, initial);
  await token.waitForDeployment();

  console.log("SCG_ADDRESS:", await token.getAddress());
}

main().catch((e) => { console.error(e); process.exit(1); });
