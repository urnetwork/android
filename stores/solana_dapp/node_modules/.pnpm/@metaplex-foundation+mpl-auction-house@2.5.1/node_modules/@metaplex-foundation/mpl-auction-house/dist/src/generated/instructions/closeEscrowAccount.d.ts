import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type CloseEscrowAccountInstructionArgs = {
    escrowPaymentBump: number;
};
export declare const closeEscrowAccountStruct: beet.BeetArgsStruct<CloseEscrowAccountInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type CloseEscrowAccountInstructionAccounts = {
    wallet: web3.PublicKey;
    escrowPaymentAccount: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const closeEscrowAccountInstructionDiscriminator: number[];
export declare function createCloseEscrowAccountInstruction(accounts: CloseEscrowAccountInstructionAccounts, args: CloseEscrowAccountInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
