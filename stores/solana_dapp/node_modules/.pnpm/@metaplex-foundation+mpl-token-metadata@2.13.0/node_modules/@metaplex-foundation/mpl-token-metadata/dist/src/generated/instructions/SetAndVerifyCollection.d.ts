import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const SetAndVerifyCollectionStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type SetAndVerifyCollectionInstructionAccounts = {
    metadata: web3.PublicKey;
    collectionAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    collectionMint: web3.PublicKey;
    collection: web3.PublicKey;
    collectionMasterEditionAccount: web3.PublicKey;
    collectionAuthorityRecord?: web3.PublicKey;
};
export declare const setAndVerifyCollectionInstructionDiscriminator = 25;
export declare function createSetAndVerifyCollectionInstruction(accounts: SetAndVerifyCollectionInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
