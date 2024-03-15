import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type DepositInstructionArgs = {
    escrowPaymentBump: number;
    amount: beet.bignum;
};
export declare const depositStruct: beet.BeetArgsStruct<DepositInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type DepositInstructionAccounts = {
    wallet: web3.PublicKey;
    paymentAccount: web3.PublicKey;
    transferAuthority: web3.PublicKey;
    escrowPaymentAccount: web3.PublicKey;
    treasuryMint: web3.PublicKey;
    authority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const depositInstructionDiscriminator: number[];
export declare function createDepositInstruction(accounts: DepositInstructionAccounts, args: DepositInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
