import type {
  AndroidDetails,
  App,
  Publisher,
  Release,
  SolanaMobileDappPublisherPortal
} from "@solana-mobile/dapp-store-publishing-tools";
import { dump, load } from "js-yaml";
import Ajv from "ajv";
// eslint-disable-next-line require-extensions/require-extensions
import schemaJson from "../generated/config_schema.json" assert { type: "json" };
import fs from "fs";
import path from "path";
import { toMetaplexFile } from "@metaplex-foundation/js";
import { Constants, showMessage } from "../CliUtils.js";
import util from "util";
import { imageSize } from "image-size";
import { exec } from "child_process";

const runImgSize = util.promisify(imageSize);
const runExec = util.promisify(exec);

export interface PublishDetails {
  publisher: Publisher;
  app: App;
  release: Release;
  solana_mobile_dapp_publisher_portal: SolanaMobileDappPublisherPortal;
}

const AaptPrefixes = {
  quoteRegex: "'(.*?)'",
  quoteNonLazyRegex: "'(.*)'",
  packagePrefix: "package: name=",
  verCodePrefix: "versionCode=",
  verNamePrefix: "versionName=",
  sdkPrefix: "sdkVersion:",
  debuggableApkPrefix: "application-debuggable",
  localePrefix: "locales: ",
};

type SaveToConfigArgs = {
  publisher?: Pick<Publisher, "address">;
  app?: Pick<App, "address">;
  release?: Pick<Release, "address">;
};

const ajv = new Ajv({ strictTuples: false });
const validate = ajv.compile(schemaJson);

export const loadPublishDetails = async (configPath: string) => {
  const configFile = await fs.promises.readFile(configPath, "utf-8");

  const valid = validate(load(configFile) as object);

  if (!valid) {
    console.error(validate.errors);
    process.exit(1);
  }

  return load(configFile) as PublishDetails;
};

export const loadPublishDetailsWithChecks = async (
  buildToolsDir: string | null = null
): Promise<PublishDetails> => {
  const config = await loadPublishDetails(Constants.getConfigFilePath());

  // We validate that the config is going to have at least one installable asset
  const apkEntry = config.release.files.find(
    (asset: PublishDetails["release"]["files"][0]) => asset.purpose === "install"
  )!;
  const apkPath = path.join(process.cwd(), apkEntry?.uri);
  if (!fs.existsSync(apkPath)) {
    throw new Error("Invalid path to APK file.");
  }

  if (buildToolsDir) {
    config.release.android_details = await getAndroidDetails(
      buildToolsDir,
      apkPath
    );
  }

  const publisherIcon = config.publisher.media?.find(
    (asset: any) => asset.purpose === "icon"
  )?.uri;

  if (publisherIcon) {
    const iconPath = path.join(process.cwd(), publisherIcon);
    await checkIconCompatibility(iconPath, "Publisher");

    const iconBuffer = await fs.promises.readFile(iconPath);
    config.publisher.icon = toMetaplexFile(iconBuffer, publisherIcon);
  }

  const appIcon = config.app.media?.find(
    (asset: any) => asset.purpose === "icon"
  )?.uri;

  if (appIcon) {
    const iconPath = path.join(process.cwd(), appIcon);
    await checkIconCompatibility(iconPath, "App");

    const iconBuffer = await fs.promises.readFile(iconPath);
    config.app.icon = toMetaplexFile(iconBuffer, appIcon);
  }

  const releaseIcon = config.release.media?.find(
    (asset: any) => asset.purpose === "icon"
  )?.uri;

  if (releaseIcon) {
    const iconPath = path.join(process.cwd(), releaseIcon);
    await checkIconCompatibility(iconPath, "Release");
  }

  if (!appIcon && !releaseIcon) {
    throw new Error("Please specify at least one media entry of type icon in your configuration file");
  }

  config.release.media.forEach((item: PublishDetails["release"]["media"][0]) => {
      const imagePath = path.join(process.cwd(), item.uri);
      if (!fs.existsSync(imagePath) || !checkImageExtension(imagePath)) {
        throw new Error(`Invalid media path or file type: ${item.uri}. Please ensure the file is a jpeg, png, or webp file.`);
      }
    }
  );

  validateLocalizableResources(config);

  const googlePkg = config.solana_mobile_dapp_publisher_portal.google_store_package;
  if (googlePkg?.length) {
    const pkgCompare = new RegExp("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+").exec(googlePkg);

    if (!pkgCompare?.length) {
      throw new Error("Please provide a valid Google store package name in the Publisher Portal section of your configuration file.");
    }
  }

  return config;
};

const checkIconCompatibility = async (path: string, typeString: string) => {
  if (!fs.existsSync(path) || !checkImageExtension(path)) {
    throw new Error(`Please check the path to your ${typeString} icon and ensure the file is a jpeg, png, or webp file.`);
  }

  if (await checkIconDimensions(path)) {
    throw new Error("Icons must have square dimensions and be no greater than 512px by 512px.");
  }
};

const checkImageExtension = (uri: string): boolean => {
  const fileExt = path.extname(uri).toLowerCase();
  return (
    fileExt == ".png" ||
    fileExt == ".jpg" ||
    fileExt == ".jpeg" ||
    fileExt == ".webp"
  );
};

/**
 * We need to pre-check some things in the localized resources before we move forward
 */
const validateLocalizableResources = (config: PublishDetails) => {
  if (!config.release.catalog["en-US"]) {
    throw new Error("Please ensure you have the en-US locale strings in your configuration file.");
  }

  const baselineSize = Object.keys(config.release.catalog["en-US"]).length;
  Object.keys(config.release.catalog).forEach((locale) => {
    const size = Object.keys(config.release.catalog[locale]).length;

    if (size != baselineSize) {
      throw new Error("Please ensure you have included all localized strings for all locales in your configuration file.");
    }
  });

  const descsWrongLength = Object.values(config.release.catalog)
    .map((x) => x.short_description)
    .filter((desc) => !desc?.length || desc.length > 30);

  if (descsWrongLength.length > 0) {
    throw new Error("Please ensure all translations of short_description are between 0 and 30 characters");
  }
};

const checkIconDimensions = async (iconPath: string): Promise<boolean> => {
  const size = await runImgSize(iconPath);

  return size?.width != size?.height || (size?.width ?? 0) > 512;
};

const getAndroidDetails = async (
  aaptDir: string,
  apkPath: string
): Promise<AndroidDetails> => {
  try {
    const { stdout } = await runExec(`${aaptDir}/aapt2 dump badging "${apkPath}"`);

    const appPackage = new RegExp(
      AaptPrefixes.packagePrefix + AaptPrefixes.quoteRegex
    ).exec(stdout);
    const versionCode = new RegExp(
      AaptPrefixes.verCodePrefix + AaptPrefixes.quoteRegex
    ).exec(stdout);
    const versionName = new RegExp(
      AaptPrefixes.verNamePrefix + AaptPrefixes.quoteRegex
    ).exec(stdout);
    const minSdk = new RegExp(
      AaptPrefixes.sdkPrefix + AaptPrefixes.quoteRegex
    ).exec(stdout);
    const permissions = [...stdout.matchAll(/uses-permission: name='(.*)'/g)];
    const locales = new RegExp(
      AaptPrefixes.localePrefix + AaptPrefixes.quoteNonLazyRegex
    ).exec(stdout);
    const isDebuggable = new RegExp(
      AaptPrefixes.debuggableApkPrefix
    ).exec(stdout);

    if (isDebuggable != null) {
      throw new TypeError("Debug apks are not supported on Solana dApp store.\nSubmit a signed release apk")
    }

    let localeArray = Array.from(locales?.values() ?? []);
    if (localeArray.length == 2) {
      const localesSrc = localeArray[1];
      localeArray = ["en-US"].concat(localesSrc.split("' '").slice(1));
    }

    if (localeArray.length >= 60) {
      showMessage(
        "The bundle apk claims supports for following locales",
        "Claim for supported locales::\n" +
        localeArray +
        "\nIf this release does not support all these locales the release may be rejected" +
        "\nSee details at https://developer.android.com/guide/topics/resources/multilingual-support#design for configuring the supported locales",
        "warning"
      );
    }

    return {
      android_package: appPackage?.[1] ?? "",
      min_sdk: parseInt(minSdk?.[1] ?? "0", 10),
      version_code: parseInt(versionCode?.[1] ?? "0", 10),
      version: versionName?.[1] ?? "0",
      cert_fingerprint: await extractCertFingerprint(aaptDir, apkPath),
      permissions: permissions.flatMap(permission => permission[1]),
      locales: localeArray
    };
  } catch (e) {
    if (e instanceof TypeError) {
      throw e
    } else {
      throw new Error(`There was an error parsing your APK. Please ensure you have installed Java and provided a valid Android tools directory containing AAPT2.\n` + e);
    }
  }
};

export const extractCertFingerprint = async (aaptDir: string, apkPath: string): Promise<string> => {
  const { stdout } = await runExec(`${aaptDir}/apksigner verify --print-certs -v "${apkPath}"`);

  const regex = /Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)/;
  const match = stdout.match(regex);

  if (match && match[1]) {
    return match[1];
  } else {
    throw new Error("Could not obtain cert fingerprint")
  }
}

export const writeToPublishDetails = async ({ publisher, app, release }: SaveToConfigArgs) => {
  const currentConfig = await loadPublishDetailsWithChecks();

  delete currentConfig.publisher.icon;
  delete currentConfig.app.icon;

  const newConfig: PublishDetails = {
    publisher: {
      ...currentConfig.publisher,
      address: publisher?.address ?? currentConfig.publisher.address
    },
    app: {
      ...currentConfig.app,
      address: app?.address ?? currentConfig.app.address
    },
    release: {
      ...currentConfig.release,
      address: release?.address ?? currentConfig.release.address
    },
    solana_mobile_dapp_publisher_portal: currentConfig.solana_mobile_dapp_publisher_portal
  };

  fs.writeFileSync(Constants.getConfigFilePath(), dump(newConfig, {
    lineWidth: -1
  }));
};
