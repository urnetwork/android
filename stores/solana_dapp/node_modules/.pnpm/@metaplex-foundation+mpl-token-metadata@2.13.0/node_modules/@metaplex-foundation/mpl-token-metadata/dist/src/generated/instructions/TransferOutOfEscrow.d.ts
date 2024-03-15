import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { TransferOutOfEscrowArgs } from '../types/TransferOutOfEscrowArgs';
export type TransferOutOfEscrowInstructionArgs = {
    transferOutOfEscrowArgs: TransferOutOfEscrowArgs;
};
export declare const TransferOutOfEscrowStruct: beet.BeetArgsStruct<TransferOutOfEscrowInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type TransferOutOfEscrowInstructionAccounts = {
    escrow: web3.PublicKey;
    metadata: web3.PublicKey;
    payer: web3.PublicKey;
    attributeMint: web3.PublicKey;
    attributeSrc: web3.PublicKey;
    attributeDst: web3.PublicKey;
    escrowMint: web3.PublicKey;
    escrowAccount: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    sysvarInstructions: web3.PublicKey;
    authority?: web3.PublicKey;
};
export declare const transferOutOfEscrowInstructionDiscriminator = 40;
export declare function createTransferOutOfEscrowInstruction(accounts: TransferOutOfEscrowInstructionAccounts, args: TransferOutOfEscrowInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
