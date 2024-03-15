import type { Metaplex, MetaplexFile } from "@metaplex-foundation/js";
import type { Keypair } from "@solana/web3.js";
import type {
  ReleaseJsonMetadata,
  AppJsonMetadata,
  PublisherJsonMetadata,
} from "./generated";

export type { ReleaseJsonMetadata } from "./generated";

export type PublisherMetadata = Omit<PublisherJsonMetadata, "image"> & {
  image: string | MetaplexFile;
};

export type AppMetadata = Omit<AppJsonMetadata, "image"> & {
  image: string | MetaplexFile;
};

export type MetaplexFileReleaseJsonMetadata = Omit<ReleaseJsonMetadata, "image"> & {
  image: string | MetaplexFile;
  extensions: {
    solana_dapp_store: {
      media: { uri: MetaplexFile }[];
      files: { uri: MetaplexFile }[];
    };
  };
};

export type Context = {
  publisher: Keypair;
  metaplex: Metaplex;
};

export type AndroidDetails = {
  android_package: string;
  min_sdk: number;
  version_code: number;
  version: string;
  permissions: string[];
  locales: string[];
};

export type Publisher = {
  address: string;
  name: string;
  icon?: string | MetaplexFile;
  website: string;
  email: string;
  media: ReleaseJsonMetadata["extensions"]["solana_dapp_store"]["media"];
};

export type App = {
  name: string;
  icon?: string | MetaplexFile;
  address: string;
  android_package: string;
  urls: {
    license_url: string;
    copyright_url: string;
    privacy_policy_url: string;
    website: string;
  };
  media: ReleaseJsonMetadata["extensions"]["solana_dapp_store"]["media"];
};

type ArrayElement<A> = A extends readonly (infer T)[] ? T : never;

export type Release = {
  address: string;
  media: ReleaseJsonMetadata["extensions"]["solana_dapp_store"]["media"];
  files: ReleaseJsonMetadata["extensions"]["solana_dapp_store"]["files"];
  android_details: AndroidDetails;
  catalog: {
    [locale: string]: {
      name: string;
      long_description: string;
      new_in_version: string;
      saga_features: string;
      short_description: string;
    };
  };
};

export type SolanaMobileDappPublisherPortal = {
  google_store_package: string;
  testing_instructions: string;
};
