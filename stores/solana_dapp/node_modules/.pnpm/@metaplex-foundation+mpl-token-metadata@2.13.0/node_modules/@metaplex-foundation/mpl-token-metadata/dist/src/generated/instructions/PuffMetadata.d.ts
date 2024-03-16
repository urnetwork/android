import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const PuffMetadataStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type PuffMetadataInstructionAccounts = {
    metadata: web3.PublicKey;
};
export declare const puffMetadataInstructionDiscriminator = 14;
export declare function createPuffMetadataInstruction(accounts: PuffMetadataInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
