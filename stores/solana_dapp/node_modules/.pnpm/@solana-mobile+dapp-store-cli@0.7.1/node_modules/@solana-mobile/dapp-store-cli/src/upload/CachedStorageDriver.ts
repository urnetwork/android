import fs from "fs";
import type { MetaplexFile, StorageDriver } from "@metaplex-foundation/js";
import { createHash } from "crypto";

type URI = string;

type Asset = {
  path: string;
  sha256: string;
  uri: URI;
};

export type AssetManifestSchema = {
  schema_version: string;
  assets: {
    [path: string]: Asset;
  };
};

// TODO(jon): We need to manage the removal / replacement of assets in the manifest
export class CachedStorageDriver implements StorageDriver {
  // NOTE: this schema version is independent of the publishing JSON schema. It should be updated
  // when the AssetManifestSchema or Asset types are updated.
  static readonly SCHEMA_VERSION = "0.1";

  assetManifest: AssetManifestSchema;
  assetManifestPath: string;
  storageDriver: StorageDriver;

  constructor(
    storageDriver: StorageDriver,
    { assetManifestPath }: { assetManifestPath: string }
  ) {
    this.assetManifestPath = assetManifestPath;
    console.info({ loading: true });
    this.assetManifest = this.loadAssetManifest(assetManifestPath) ?? {
      schema_version: CachedStorageDriver.SCHEMA_VERSION,
      assets: {},
    };
    this.storageDriver = storageDriver;
  }

  async getUploadPrice(bytes: number) {
    return this.storageDriver.getUploadPrice(bytes);
  }

  loadAssetManifest(filename: string): AssetManifestSchema | undefined {
    try {
      return JSON.parse(
        fs.readFileSync(filename, "utf-8")
      ) as AssetManifestSchema;
    } catch (error) {
      console.warn(`Failed opening ${filename}; initializing with a blank asset manifest`);
      return;
    }
  }

  uploadedAsset(filename: string, { sha256 }: { sha256: string }) {
    if (this.assetManifest.assets[filename]?.sha256 === sha256) {
      return this.assetManifest.assets[filename];
    }
    return null;
  }

  async upload(file: MetaplexFile): Promise<string> {
    // `inline.json` is the NFT-related metadata. This data is not stable so we'll skip the caching step
    if (file.fileName === "inline.json") {
      return await this.storageDriver.upload(file);
    }
    const hash = createHash("sha256").update(file.buffer).digest("base64");

    console.info(
      JSON.stringify({
        file: {
          name: file.fileName,
          disn: file.displayName,
          un: file.uniqueName,
        },
      })
    );
    const uploadedAsset = this.uploadedAsset(file.fileName, { sha256: hash });
    if (uploadedAsset) {
      console.log(
        `Asset ${file.fileName} already uploaded at ${uploadedAsset.uri}`
      );
      return uploadedAsset.uri;
    }

    console.log(`Uploading ${file.fileName}`);
    const uri = await this.storageDriver.upload(file);

    this.assetManifest.assets[file.fileName] = {
      path: file.fileName,
      sha256: hash,
      uri,
    };

    await fs.promises.writeFile(
      `${process.cwd()}/${this.assetManifestPath}`,
      // Something is really weird, I can't seem to stringify `this.assetManifest` straight-up. Here be dragons
      JSON.stringify({ assets: { ...this.assetManifest.assets } }, null, 2),
      "utf-8"
    );

    return uri;
  }
}
