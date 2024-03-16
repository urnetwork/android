import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const VerifySizedCollectionItemStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type VerifySizedCollectionItemInstructionAccounts = {
    metadata: web3.PublicKey;
    collectionAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    collectionMint: web3.PublicKey;
    collection: web3.PublicKey;
    collectionMasterEditionAccount: web3.PublicKey;
    collectionAuthorityRecord?: web3.PublicKey;
};
export declare const verifySizedCollectionItemInstructionDiscriminator = 30;
export declare function createVerifySizedCollectionItemInstruction(accounts: VerifySizedCollectionItemInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
