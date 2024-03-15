import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { CreateMetadataAccountArgsV3 } from '../types/CreateMetadataAccountArgsV3';
export type CreateMetadataAccountV3InstructionArgs = {
    createMetadataAccountArgsV3: CreateMetadataAccountArgsV3;
};
export declare const CreateMetadataAccountV3Struct: beet.FixableBeetArgsStruct<CreateMetadataAccountV3InstructionArgs & {
    instructionDiscriminator: number;
}>;
export type CreateMetadataAccountV3InstructionAccounts = {
    metadata: web3.PublicKey;
    mint: web3.PublicKey;
    mintAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const createMetadataAccountV3InstructionDiscriminator = 33;
export declare function createCreateMetadataAccountV3Instruction(accounts: CreateMetadataAccountV3InstructionAccounts, args: CreateMetadataAccountV3InstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
