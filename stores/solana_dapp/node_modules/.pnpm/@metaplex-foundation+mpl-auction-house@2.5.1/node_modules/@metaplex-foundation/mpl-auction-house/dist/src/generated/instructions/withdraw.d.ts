import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type WithdrawInstructionArgs = {
    escrowPaymentBump: number;
    amount: beet.bignum;
};
export declare const withdrawStruct: beet.BeetArgsStruct<WithdrawInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type WithdrawInstructionAccounts = {
    wallet: web3.PublicKey;
    receiptAccount: web3.PublicKey;
    escrowPaymentAccount: web3.PublicKey;
    treasuryMint: web3.PublicKey;
    authority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const withdrawInstructionDiscriminator: number[];
export declare function createWithdrawInstruction(accounts: WithdrawInstructionAccounts, args: WithdrawInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
