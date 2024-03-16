import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
export type Collection = {
    verified: boolean;
    key: web3.PublicKey;
};
export declare const collectionBeet: beet.BeetArgsStruct<Collection>;
