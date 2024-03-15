import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type WithdrawFromFeeInstructionArgs = {
    amount: beet.bignum;
};
export declare const withdrawFromFeeStruct: beet.BeetArgsStruct<WithdrawFromFeeInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type WithdrawFromFeeInstructionAccounts = {
    authority: web3.PublicKey;
    feeWithdrawalDestination: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const withdrawFromFeeInstructionDiscriminator: number[];
export declare function createWithdrawFromFeeInstruction(accounts: WithdrawFromFeeInstructionAccounts, args: WithdrawFromFeeInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
