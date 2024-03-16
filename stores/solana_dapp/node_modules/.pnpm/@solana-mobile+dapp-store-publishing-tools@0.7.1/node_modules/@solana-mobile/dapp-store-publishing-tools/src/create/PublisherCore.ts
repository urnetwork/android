import type { TransactionBuilder } from "@metaplex-foundation/js";
import debugModule from "debug";
import type { Signer } from "@solana/web3.js";

import { validatePublisher } from "../validate/CoreValidation.js";
import { Constants, mintNft } from "../CoreUtils.js";
import type { Context, Publisher, PublisherMetadata } from "../types.js";

const debug = debugModule("PUBLISHER");

export const createPublisherJson = (
  publisher: Publisher
): PublisherMetadata => {
  const publisherMetadata = {
    schema_version: Constants.PUBLISHING_SCHEMA_VER,
    name: publisher.name,
    image: publisher.icon!,
    external_url: publisher.website,
    properties: {
      category: "dApp",
      creators: [
        {
          address: publisher.address,
          share: 100,
        },
      ],
    },
    extensions: {
      solana_dapp_store: {
        publisher_details: {
          name: publisher.name,
          website: publisher.website,
          contact: publisher.email,
        },
      },
    },
  };

  return publisherMetadata;
};

type CreatePublisherInput = {
  mintAddress: Signer;
  publisherDetails: Publisher;
};

export const createPublisher = async (
  { mintAddress, publisherDetails }: CreatePublisherInput,
  { metaplex }: Context
): Promise<TransactionBuilder> => {
  debug(`Minting publisher NFT`);

  const publisherJson = createPublisherJson(publisherDetails);
  validatePublisher(publisherJson);

  const txBuilder = await mintNft(metaplex, publisherJson, {
    isCollection: true,
    isMutable: true,
    useNewMint: mintAddress,
  });

  return txBuilder;
};
