import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { DataV2 } from './DataV2';
export type UpdateMetadataAccountArgsV2 = {
    data: beet.COption<DataV2>;
    updateAuthority: beet.COption<web3.PublicKey>;
    primarySaleHappened: beet.COption<boolean>;
    isMutable: beet.COption<boolean>;
};
export declare const updateMetadataAccountArgsV2Beet: beet.FixableBeetArgsStruct<UpdateMetadataAccountArgsV2>;
