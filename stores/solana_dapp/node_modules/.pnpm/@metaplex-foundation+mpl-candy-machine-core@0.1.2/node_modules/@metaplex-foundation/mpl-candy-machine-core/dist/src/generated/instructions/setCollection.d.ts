import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const setCollectionStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number[];
}>;
export declare type SetCollectionInstructionAccounts = {
    candyMachine: web3.PublicKey;
    authority: web3.PublicKey;
    authorityPda: web3.PublicKey;
    payer: web3.PublicKey;
    collectionMint: web3.PublicKey;
    collectionMetadata: web3.PublicKey;
    collectionAuthorityRecord: web3.PublicKey;
    newCollectionUpdateAuthority: web3.PublicKey;
    newCollectionMetadata: web3.PublicKey;
    newCollectionMint: web3.PublicKey;
    newCollectionMasterEdition: web3.PublicKey;
    newCollectionAuthorityRecord: web3.PublicKey;
    tokenMetadataProgram: web3.PublicKey;
    systemProgram?: web3.PublicKey;
};
export declare const setCollectionInstructionDiscriminator: number[];
export declare function createSetCollectionInstruction(accounts: SetCollectionInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
