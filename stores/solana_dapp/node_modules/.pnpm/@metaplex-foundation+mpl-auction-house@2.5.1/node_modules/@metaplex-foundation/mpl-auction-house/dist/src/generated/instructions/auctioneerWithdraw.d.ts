import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type AuctioneerWithdrawInstructionArgs = {
    escrowPaymentBump: number;
    amount: beet.bignum;
};
export declare const auctioneerWithdrawStruct: beet.BeetArgsStruct<AuctioneerWithdrawInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type AuctioneerWithdrawInstructionAccounts = {
    wallet: web3.PublicKey;
    receiptAccount: web3.PublicKey;
    escrowPaymentAccount: web3.PublicKey;
    treasuryMint: web3.PublicKey;
    authority: web3.PublicKey;
    auctioneerAuthority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    ahAuctioneerPda: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const auctioneerWithdrawInstructionDiscriminator: number[];
export declare function createAuctioneerWithdrawInstruction(accounts: AuctioneerWithdrawInstructionAccounts, args: AuctioneerWithdrawInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
