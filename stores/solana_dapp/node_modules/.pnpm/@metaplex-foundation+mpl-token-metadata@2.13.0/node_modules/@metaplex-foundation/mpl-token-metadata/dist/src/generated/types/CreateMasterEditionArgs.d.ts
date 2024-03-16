import * as beet from '@metaplex-foundation/beet';
export type CreateMasterEditionArgs = {
    maxSupply: beet.COption<beet.bignum>;
};
export declare const createMasterEditionArgsBeet: beet.FixableBeetArgsStruct<CreateMasterEditionArgs>;
