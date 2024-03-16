import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type CreateAuctionHouseInstructionArgs = {
    bump: number;
    feePayerBump: number;
    treasuryBump: number;
    sellerFeeBasisPoints: number;
    requiresSignOff: boolean;
    canChangeSalePrice: boolean;
};
export declare const createAuctionHouseStruct: beet.BeetArgsStruct<CreateAuctionHouseInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type CreateAuctionHouseInstructionAccounts = {
    treasuryMint: web3.PublicKey;
    payer: web3.PublicKey;
    authority: web3.PublicKey;
    feeWithdrawalDestination: web3.PublicKey;
    treasuryWithdrawalDestination: web3.PublicKey;
    treasuryWithdrawalDestinationOwner: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    auctionHouseTreasury: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const createAuctionHouseInstructionDiscriminator: number[];
export declare function createCreateAuctionHouseInstruction(accounts: CreateAuctionHouseInstructionAccounts, args: CreateAuctionHouseInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
