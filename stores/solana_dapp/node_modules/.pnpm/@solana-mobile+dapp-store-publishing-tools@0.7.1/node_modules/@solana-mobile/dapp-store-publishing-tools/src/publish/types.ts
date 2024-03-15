import { Connection } from "@solana/web3.js";

export type SignWithPublisherKeypair = (buf: Buffer) => Buffer;

export type PublishSolanaNetworkInput = {
  connection: Connection;
  sign: SignWithPublisherKeypair;
};
