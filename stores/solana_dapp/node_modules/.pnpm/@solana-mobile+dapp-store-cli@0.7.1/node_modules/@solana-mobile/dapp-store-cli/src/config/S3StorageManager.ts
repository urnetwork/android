import { EnvVariables, S3Config } from "./EnvVariables.js";

export class S3StorageManager {
  private _config: S3Config | undefined = undefined

  public get hasS3Config(): boolean {
    return this._config != undefined;
  }

  public get s3Config(): S3Config {
    return this._config as S3Config;
  }

  constructor(private envVars: EnvVariables) {
    if (envVars.hasS3EnvArgs) {
      this._config = {
        accessKey: this.envVars.s3Config.accessKey,
        secretKey: this.envVars.s3Config.secretKey,
        bucketName: this.envVars.s3Config.bucketName,
        regionName: this.envVars.s3Config.regionName
      };
    }
  }

  parseCmdArg(cmdArg: string) {
    if (!cmdArg || cmdArg == "") return;

    try {
      //This will overwrite any existing parameters already obtained from the .env file
      const parsedArray = JSON.parse(`${cmdArg}`);

      if (parsedArray instanceof Array && parsedArray[0] == "s3") {
        if (parsedArray.length != 5) throw new Error("Invalid parameters")

        this._config = {
          accessKey: parsedArray[1],
          secretKey: parsedArray[2],
          bucketName: parsedArray[3],
          regionName: parsedArray[4]
        };
      }
    } catch (e) {
      throw new Error("There was an error parsing your s3 parameters from the CLI. Please ensure they are formatted correctly.");
    }
  }

}