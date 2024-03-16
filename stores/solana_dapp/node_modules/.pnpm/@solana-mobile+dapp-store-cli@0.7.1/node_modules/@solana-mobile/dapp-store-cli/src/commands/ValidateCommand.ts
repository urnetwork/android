import {
  createAppJson,
  createPublisherJson,
  createReleaseJson,
  validateApp,
  validatePublisher,
  validateRelease,
  metaplexFileReplacer,
} from "@solana-mobile/dapp-store-publishing-tools";
import { debug } from "../CliUtils.js";

import type { Keypair } from "@solana/web3.js";
import type { MetaplexFile } from "@metaplex-foundation/js";
import { isMetaplexFile } from "@metaplex-foundation/js";
import { loadPublishDetailsWithChecks } from "../config/PublishDetails.js";

export const validateCommand = async ({
  signer,
  buildToolsPath,
}: {
  signer: Keypair;
  buildToolsPath?: string;
}) => {
  const {
    publisher: publisherDetails,
    app: appDetails,
    release: releaseDetails,
  } = await loadPublishDetailsWithChecks(buildToolsPath);

  debug({ publisherDetails, appDetails, releaseDetails });

  const publisherJson = createPublisherJson(publisherDetails);
  if (typeof publisherJson.image !== "string") {
    publisherJson.image = (publisherJson.image as MetaplexFile)?.fileName;
  }
  debug("publisherJson=", JSON.stringify({ publisherJson }, metaplexFileReplacer, 2));

  try {
    validatePublisher(publisherJson);
    console.info(`Publisher JSON valid!`);
  } catch (e) {
    console.error(e);
  }

  const appJson = createAppJson(appDetails, signer.publicKey);
  if (typeof appJson.image !== "string") {
    appJson.image = (appJson.image as MetaplexFile)?.fileName;
  }
  debug("appJson=", JSON.stringify({ appJson }, metaplexFileReplacer, 2));

  try {
    validateApp(appJson);
    console.info(`App JSON valid!`);
  } catch (e) {
    console.error(e);
  }

  const releaseJson = await createReleaseJson(
    { releaseDetails, appDetails, publisherDetails },
    signer.publicKey
  );

  const objStringified = JSON.stringify(releaseJson, metaplexFileReplacer, 2);
  debug("releaseJson=", objStringified);

  try {
    validateRelease(JSON.parse(objStringified));
    console.info(`Release JSON valid!`);
  } catch (e) {
    console.error(e);
  }
};
