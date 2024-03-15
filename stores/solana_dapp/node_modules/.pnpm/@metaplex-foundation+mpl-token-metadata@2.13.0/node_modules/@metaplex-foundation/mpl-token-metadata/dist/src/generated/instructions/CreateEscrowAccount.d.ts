import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const CreateEscrowAccountStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type CreateEscrowAccountInstructionAccounts = {
    escrow: web3.PublicKey;
    metadata: web3.PublicKey;
    mint: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    edition: web3.PublicKey;
    payer: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    sysvarInstructions: web3.PublicKey;
    authority?: web3.PublicKey;
};
export declare const createEscrowAccountInstructionDiscriminator = 38;
export declare function createCreateEscrowAccountInstruction(accounts: CreateEscrowAccountInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
