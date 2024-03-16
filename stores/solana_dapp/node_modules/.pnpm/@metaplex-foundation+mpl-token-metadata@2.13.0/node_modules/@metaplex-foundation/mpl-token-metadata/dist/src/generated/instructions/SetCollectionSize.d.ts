import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { SetCollectionSizeArgs } from '../types/SetCollectionSizeArgs';
export type SetCollectionSizeInstructionArgs = {
    setCollectionSizeArgs: SetCollectionSizeArgs;
};
export declare const SetCollectionSizeStruct: beet.BeetArgsStruct<SetCollectionSizeInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type SetCollectionSizeInstructionAccounts = {
    collectionMetadata: web3.PublicKey;
    collectionAuthority: web3.PublicKey;
    collectionMint: web3.PublicKey;
    collectionAuthorityRecord?: web3.PublicKey;
};
export declare const setCollectionSizeInstructionDiscriminator = 34;
export declare function createSetCollectionSizeInstruction(accounts: SetCollectionSizeInstructionAccounts, args: SetCollectionSizeInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
