import * as beet from '@metaplex-foundation/beet';
import { DataV2 } from './DataV2';
import { CollectionDetails } from './CollectionDetails';
export type CreateMetadataAccountArgsV3 = {
    data: DataV2;
    isMutable: boolean;
    collectionDetails: beet.COption<CollectionDetails>;
};
export declare const createMetadataAccountArgsV3Beet: beet.FixableBeetArgsStruct<CreateMetadataAccountArgsV3>;
