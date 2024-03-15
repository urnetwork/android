import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
export type Creator = {
    address: web3.PublicKey;
    verified: boolean;
    share: number;
};
export declare const creatorBeet: beet.BeetArgsStruct<Creator>;
