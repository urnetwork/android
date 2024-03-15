import { Constants, mintNft } from "../CoreUtils.js";
import type { App, AppMetadata, Context } from "../types.js";
import type { PublicKey, Signer } from "@solana/web3.js";
import debugModule from "debug";
import { validateApp } from "../validate/CoreValidation.js";

const debug = debugModule("APP");

export const createAppJson = (
  app: App,
  publisherAddress: PublicKey
): AppMetadata => {
  const appMetadata = {
    schema_version: Constants.PUBLISHING_SCHEMA_VER,
    name: app.name,
    image: app.icon!,
    external_url: app.urls.website,
    properties: {
      category: "dApp",
      creators: [
        {
          address: publisherAddress.toBase58(),
          share: 100,
        },
      ],
    },
    extensions: {
      solana_dapp_store: {
        android_package: app.android_package,
      },
    },
  };

  return appMetadata;
};

type CreateAppInput = {
  publisherMintAddress: PublicKey;
  mintAddress: Signer;
  appDetails: App;
};

export const createApp = async (
  { publisherMintAddress, mintAddress, appDetails }: CreateAppInput,
  { metaplex, publisher }: Context
) => {
  debug(`Minting app NFT for publisher: ${publisherMintAddress.toBase58()}`);

  const appJson = createAppJson(appDetails, publisher.publicKey);
  validateApp(appJson);

  const txBuilder = await mintNft(metaplex, appJson, {
    useNewMint: mintAddress,
    collection: publisherMintAddress,
    collectionAuthority: publisher,
    isCollection: true,
    isMutable: true,
    creators: [{ address: publisher.publicKey, share: 100 }],
  });

  txBuilder.append(
    metaplex.nfts().builders().verifyCreator({
      mintAddress: mintAddress.publicKey,
      creator: publisher,
    })
  );

  debug({ appNft: mintAddress.publicKey.toBase58() });

  return txBuilder;
};
