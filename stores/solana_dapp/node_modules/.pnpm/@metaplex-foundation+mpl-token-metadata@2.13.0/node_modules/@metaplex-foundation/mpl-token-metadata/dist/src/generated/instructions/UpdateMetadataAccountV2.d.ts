import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { UpdateMetadataAccountArgsV2 } from '../types/UpdateMetadataAccountArgsV2';
export type UpdateMetadataAccountV2InstructionArgs = {
    updateMetadataAccountArgsV2: UpdateMetadataAccountArgsV2;
};
export declare const UpdateMetadataAccountV2Struct: beet.FixableBeetArgsStruct<UpdateMetadataAccountV2InstructionArgs & {
    instructionDiscriminator: number;
}>;
export type UpdateMetadataAccountV2InstructionAccounts = {
    metadata: web3.PublicKey;
    updateAuthority: web3.PublicKey;
};
export declare const updateMetadataAccountV2InstructionDiscriminator = 15;
export declare function createUpdateMetadataAccountV2Instruction(accounts: UpdateMetadataAccountV2InstructionAccounts, args: UpdateMetadataAccountV2InstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
