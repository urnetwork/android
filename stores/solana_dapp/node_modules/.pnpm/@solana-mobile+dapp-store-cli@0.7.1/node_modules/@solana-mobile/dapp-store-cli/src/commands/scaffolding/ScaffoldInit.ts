import yaml, { dump } from "js-yaml";

// eslint-disable-next-line require-extensions/require-extensions
import releaseSchema from "../../generated/config_obj.json" assert { type: "json" };
import fs from "fs";
import { Constants } from "../../CliUtils.js";

export const initScaffold = (): string => {
  const outputYaml = Constants.CONFIG_FILE_NAME;
  const outFile = `${process.cwd()}/${outputYaml}`;

  if (fs.existsSync(outFile)) {
    throw Error("Configuration file already present; please use to intialize a new config file.");
  }

  fs.writeFileSync(outFile, dump(releaseSchema));

  return `Your configuration file was created: ${outputYaml}`;
};