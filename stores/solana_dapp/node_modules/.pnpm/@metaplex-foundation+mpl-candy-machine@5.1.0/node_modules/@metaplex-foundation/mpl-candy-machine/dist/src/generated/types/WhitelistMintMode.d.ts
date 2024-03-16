import * as beet from '@metaplex-foundation/beet';
export declare enum WhitelistMintMode {
    BurnEveryTime = 0,
    NeverBurn = 1
}
export declare const whitelistMintModeBeet: beet.FixedSizeBeet<WhitelistMintMode, WhitelistMintMode>;
