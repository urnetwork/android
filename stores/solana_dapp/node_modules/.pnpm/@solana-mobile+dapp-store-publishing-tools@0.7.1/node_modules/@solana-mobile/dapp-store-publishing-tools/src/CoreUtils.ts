import type {
  CreateNftInput,
  JsonMetadata,
  Metaplex,
  MetaplexFile,
  TransactionBuilder,
} from "@metaplex-foundation/js";
import { ComputeBudgetProgram } from "@solana/web3.js";

export class Constants {
  static PUBLISHING_SCHEMA_VER = "0.2.6";
}
export const truncateAddress = (address: string) => {
  return `${address.slice(0, 4)}...${address.slice(
    address.length - 4,
    address.length
  )}`;
};

type JsonMetadataMetaplexFile = Omit<JsonMetadata, "image"> & {
  image: string | MetaplexFile;
};

export const mintNft = async (
  metaplex: Metaplex,
  json: JsonMetadataMetaplexFile,
  createNftInput: Omit<CreateNftInput, "uri" | "name" | "sellerFeeBasisPoints">
): Promise<TransactionBuilder> => {
  console.info({ json });
  const { uri } = await metaplex.nfts().uploadMetadata(json);

  const txBuilder = await metaplex
    .nfts()
    .builders()
    .create({
      ...createNftInput,
      uri,
      // @ts-ignore
      name: json.name,
      sellerFeeBasisPoints: 0,
    });

  txBuilder.prepend({
      instruction: ComputeBudgetProgram.setComputeUnitPrice({
        microLamports: 5000,
      }),
      signers: [],
    });

  return txBuilder;
};
