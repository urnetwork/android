import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const UnverifyCollectionStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type UnverifyCollectionInstructionAccounts = {
    metadata: web3.PublicKey;
    collectionAuthority: web3.PublicKey;
    collectionMint: web3.PublicKey;
    collection: web3.PublicKey;
    collectionMasterEditionAccount: web3.PublicKey;
    collectionAuthorityRecord?: web3.PublicKey;
};
export declare const unverifyCollectionInstructionDiscriminator = 22;
export declare function createUnverifyCollectionInstruction(accounts: UnverifyCollectionInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
