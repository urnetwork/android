import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const SignMetadataStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type SignMetadataInstructionAccounts = {
    metadata: web3.PublicKey;
    creator: web3.PublicKey;
};
export declare const signMetadataInstructionDiscriminator = 7;
export declare function createSignMetadataInstruction(accounts: SignMetadataInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
