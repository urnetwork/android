import { Connection, Keypair } from "@solana/web3.js";
import type { SignWithPublisherKeypair } from "@solana-mobile/dapp-store-publishing-tools";
import { publishSupport } from "@solana-mobile/dapp-store-publishing-tools";
import { checkMintedStatus } from "../../CliUtils.js";
import nacl from "tweetnacl";
import { loadPublishDetailsWithChecks } from "../../config/PublishDetails.js";

type PublishSupportCommandInput = {
  appMintAddress: string;
  releaseMintAddress: string;
  signer: Keypair;
  url: string;
  dryRun: boolean;
  requestorIsAuthorized: boolean;
  requestDetails: string;
};

export const publishSupportCommand = async ({
  appMintAddress,
  releaseMintAddress,
  signer,
  url,
  dryRun = false,
  requestorIsAuthorized = false,
  requestDetails,
}: PublishSupportCommandInput) => {
  if (!requestorIsAuthorized) {
    console.error(
      "ERROR: Cannot submit a request for which the requestor does not attest they are authorized to do so"
    );
    return;
  }

  const connection = new Connection(url);
  const {
    publisher: publisherDetails,
    app: appDetails,
    release: releaseDetails,
  } = await loadPublishDetailsWithChecks();

  const sign = ((buf: Buffer) =>
    nacl.sign(buf, signer.secretKey)) as SignWithPublisherKeypair;

  const pubAddr = publisherDetails.address;
  const appAddr = appMintAddress ?? appDetails.address;
  const releaseAddr = releaseMintAddress ?? releaseDetails.address;

  await checkMintedStatus(connection, pubAddr, appAddr, releaseAddr);

  await publishSupport(
    { connection, sign },
    {
      appMintAddress: appMintAddress ?? appDetails.address,
      releaseMintAddress: releaseMintAddress ?? releaseDetails.address,
      publisherDetails,
      requestorIsAuthorized,
      requestDetails,
    },
    dryRun
  );
};
