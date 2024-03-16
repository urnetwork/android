import fs from "fs";
import type { Connection } from "@solana/web3.js";
import { Keypair, PublicKey } from "@solana/web3.js";
import debugModule from "debug";
import {
  BundlrStorageDriver,
  keypairIdentity,
  Metaplex,
} from "@metaplex-foundation/js";
import updateNotifier from "update-notifier";
import cliPackage from "./package.json" assert { type: "json" };
import boxen from "boxen";
import ver from "semver";
import { CachedStorageDriver } from "./upload/CachedStorageDriver.js";
import { EnvVariables } from "./config/index.js";
import { S3Client } from "@aws-sdk/client-s3";
import { awsStorage } from "@metaplex-foundation/js-plugin-aws";
import { S3StorageManager } from "./config/index.js";

export class Constants {
  static CLI_VERSION = "0.7.1";
  static CONFIG_FILE_NAME = "config.yaml";
  static DEFAULT_RPC_DEVNET = "https://api.devnet.solana.com";

  static getConfigFilePath = () => {
    return `${process.cwd()}/${Constants.CONFIG_FILE_NAME}`;
  };
}

export const debug = debugModule("CLI");

export const checkForSelfUpdate = async () => {
  const notifier = updateNotifier({ pkg: cliPackage });
  const updateInfo = await notifier.fetchInfo();

  const latestVer = new ver.SemVer(updateInfo.latest);
  const currentVer = new ver.SemVer(updateInfo.current);

  if (
    latestVer.major > currentVer.major ||
    latestVer.minor > currentVer.minor
  ) {
    throw new Error(
      "Please update to the latest version of the dApp Store CLI before proceeding."
    );
  }
};

export const checkMintedStatus = async (
  conn: Connection,
  pubAddr: string,
  appAddr: string,
  releaseAddr: string
) => {
  const results = await conn.getMultipleAccountsInfo([
    new PublicKey(pubAddr),
    new PublicKey(appAddr),
    new PublicKey(releaseAddr),
  ]);

  const rentAccounts = results.filter(
    (item) => !(item == undefined) && item?.lamports > 0
  );
  if (rentAccounts?.length != 3) {
    throw new Error(
      "Please ensure you have minted all of your NFTs before submitting to the Solana Mobile dApp publisher portal."
    );
  }
};

export const parseKeypair = (pathToKeypairFile: string) => {
  try {
    const keypairFile = fs.readFileSync(pathToKeypairFile, "utf-8");
    return Keypair.fromSecretKey(Buffer.from(JSON.parse(keypairFile)));
  } catch (e) {
    showMessage(
      "KeyPair Error",
      "Something went wrong when attempting to retrieve the keypair at " +
        pathToKeypairFile,
      "error"
    );
  }
};

export const isDevnet = (rpcUrl: string): boolean => {
  return rpcUrl.indexOf("devnet") != -1;
};

export const isTestnet = (rpcUrl: string): boolean => {
  return rpcUrl.indexOf("testnet") != -1;
};

export const checkSubmissionNetwork = (rpcUrl: string) => {
  if (isDevnet(rpcUrl) || isTestnet(rpcUrl)) {
    throw new Error(
      "It looks like you are attempting to submit a request with a devnet or testnet RPC endpoint. Please ensure that your NFTs are minted on mainnet beta, and re-run with a mainnet beta RPC endpoint."
    );
  }
};

export const generateNetworkSuffix = (rpcUrl: string): string => {
  let suffix = "";

  if (isDevnet(rpcUrl)) {
    suffix = "?cluster=devnet";
  } else if (isTestnet(rpcUrl)) {
    suffix = "?cluster=testnet";
  } else {
    suffix = "?cluster=mainnet";
  }

  return suffix;
};

export const showMessage = (
  titleMessage = "",
  contentMessage = "",
  type: "standard" | "error" | "warning" = "standard"
): string => {
  let color = "cyan";
  if (type == "error") {
    color = "redBright";
  } else if (type == "warning") {
    color = "yellow";
  }

  const msg = boxen(contentMessage, {
    title: titleMessage,
    padding: 1,
    margin: 1,
    borderStyle: "single",
    borderColor: color,
    textAlignment: "left",
    titleAlignment: "center",
  });

  console.log(msg);
  return msg;
};

export const getMetaplexInstance = (
  connection: Connection,
  keypair: Keypair,
  storageParams: string = ""
) => {
  const metaplex = Metaplex.make(connection).use(keypairIdentity(keypair));
  const isDevnet = connection.rpcEndpoint.includes("devnet");

  //TODO: Use DI for this
  const s3Mgr = new S3StorageManager(new EnvVariables());
  s3Mgr.parseCmdArg(storageParams);

  if (s3Mgr.hasS3Config) {
    const awsClient = new S3Client({
      region: s3Mgr.s3Config.regionName,
      credentials: {
        accessKeyId: s3Mgr.s3Config.accessKey,
        secretAccessKey: s3Mgr.s3Config.secretKey,
      },
    });

    const bucketPlugin = awsStorage(awsClient, s3Mgr.s3Config.bucketName);
    metaplex.use(bucketPlugin);
  } else {
    const bundlrStorageDriver = isDevnet
      ? new BundlrStorageDriver(metaplex, {
        address: "https://turbo.ardrive.dev",
        providerUrl: Constants.DEFAULT_RPC_DEVNET,
      })
      : new BundlrStorageDriver(metaplex, {
        address: "https://turbo.ardrive.io",
      });

    metaplex.storage().setDriver(bundlrStorageDriver);
  }

  metaplex.storage().setDriver(
    new CachedStorageDriver(metaplex.storage().driver(), {
      assetManifestPath: isDevnet
        ? "./.asset-manifest-devnet.json"
        : "./.asset-manifest.json",
    })
  );

  return metaplex;
};
