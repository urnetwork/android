import type { S3Client } from '@aws-sdk/client-s3';
import type { MetaplexPlugin } from '@metaplex-foundation/js';
export declare const awsStorage: (client: S3Client, bucketName: string) => MetaplexPlugin;
