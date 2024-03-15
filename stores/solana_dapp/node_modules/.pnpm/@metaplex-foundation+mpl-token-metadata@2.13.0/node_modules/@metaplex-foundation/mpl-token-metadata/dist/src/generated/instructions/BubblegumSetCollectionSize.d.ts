import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { SetCollectionSizeArgs } from '../types/SetCollectionSizeArgs';
export type BubblegumSetCollectionSizeInstructionArgs = {
    setCollectionSizeArgs: SetCollectionSizeArgs;
};
export declare const BubblegumSetCollectionSizeStruct: beet.BeetArgsStruct<BubblegumSetCollectionSizeInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type BubblegumSetCollectionSizeInstructionAccounts = {
    collectionMetadata: web3.PublicKey;
    collectionAuthority: web3.PublicKey;
    collectionMint: web3.PublicKey;
    bubblegumSigner: web3.PublicKey;
    collectionAuthorityRecord?: web3.PublicKey;
};
export declare const bubblegumSetCollectionSizeInstructionDiscriminator = 36;
export declare function createBubblegumSetCollectionSizeInstruction(accounts: BubblegumSetCollectionSizeInstructionAccounts, args: BubblegumSetCollectionSizeInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
