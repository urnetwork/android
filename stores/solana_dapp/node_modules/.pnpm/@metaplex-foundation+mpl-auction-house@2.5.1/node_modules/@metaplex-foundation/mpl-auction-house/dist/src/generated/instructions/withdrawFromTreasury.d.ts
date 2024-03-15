import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type WithdrawFromTreasuryInstructionArgs = {
    amount: beet.bignum;
};
export declare const withdrawFromTreasuryStruct: beet.BeetArgsStruct<WithdrawFromTreasuryInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type WithdrawFromTreasuryInstructionAccounts = {
    treasuryMint: web3.PublicKey;
    authority: web3.PublicKey;
    treasuryWithdrawalDestination: web3.PublicKey;
    auctionHouseTreasury: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const withdrawFromTreasuryInstructionDiscriminator: number[];
export declare function createWithdrawFromTreasuryInstruction(accounts: WithdrawFromTreasuryInstructionAccounts, args: WithdrawFromTreasuryInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
