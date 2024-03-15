import * as dotenv from "dotenv";

export type S3Config = {
  accessKey: string;
  secretKey: string;
  bucketName: string;
  regionName: string;
}
export class EnvVariables {

  public get hasAndroidTools(): boolean {
    return process.env.ANDROID_TOOLS_DIR !== undefined;
  }

  public get androidToolsDir(): string {
    return process.env.ANDROID_TOOLS_DIR as string;
  }

  public get hasS3EnvArgs(): boolean {
    return process.env.STORAGE_TYPE == "s3" &&
      process.env.S3_ACCESS_KEY != undefined &&
      process.env.S3_SECRET_KEY != undefined &&
      process.env.S3_BUCKET != undefined &&
      process.env.S3_REGION != undefined;
  }

  public get s3Config(): S3Config {
    return {
      accessKey: process.env.S3_ACCESS_KEY as string,
      secretKey: process.env.S3_SECRET_KEY as string,
      bucketName: process.env.S3_BUCKET as string,
      regionName: process.env.S3_REGION as string
    };
  }

  constructor() {
    dotenv.config();
  }
}