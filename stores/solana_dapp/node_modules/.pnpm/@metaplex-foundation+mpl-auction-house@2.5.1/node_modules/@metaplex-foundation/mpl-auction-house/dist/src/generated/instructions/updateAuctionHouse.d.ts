import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type UpdateAuctionHouseInstructionArgs = {
    sellerFeeBasisPoints: beet.COption<number>;
    requiresSignOff: beet.COption<boolean>;
    canChangeSalePrice: beet.COption<boolean>;
};
export declare const updateAuctionHouseStruct: beet.FixableBeetArgsStruct<UpdateAuctionHouseInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type UpdateAuctionHouseInstructionAccounts = {
    treasuryMint: web3.PublicKey;
    payer: web3.PublicKey;
    authority: web3.PublicKey;
    newAuthority: web3.PublicKey;
    feeWithdrawalDestination: web3.PublicKey;
    treasuryWithdrawalDestination: web3.PublicKey;
    treasuryWithdrawalDestinationOwner: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const updateAuctionHouseInstructionDiscriminator: number[];
export declare function createUpdateAuctionHouseInstruction(accounts: UpdateAuctionHouseInstructionAccounts, args: UpdateAuctionHouseInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
