import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { CreateMasterEditionArgs } from '../types/CreateMasterEditionArgs';
export type CreateMasterEditionV3InstructionArgs = {
    createMasterEditionArgs: CreateMasterEditionArgs;
};
export declare const CreateMasterEditionV3Struct: beet.FixableBeetArgsStruct<CreateMasterEditionV3InstructionArgs & {
    instructionDiscriminator: number;
}>;
export type CreateMasterEditionV3InstructionAccounts = {
    edition: web3.PublicKey;
    mint: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    mintAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    metadata: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const createMasterEditionV3InstructionDiscriminator = 17;
export declare function createCreateMasterEditionV3Instruction(accounts: CreateMasterEditionV3InstructionAccounts, args: CreateMasterEditionV3InstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
