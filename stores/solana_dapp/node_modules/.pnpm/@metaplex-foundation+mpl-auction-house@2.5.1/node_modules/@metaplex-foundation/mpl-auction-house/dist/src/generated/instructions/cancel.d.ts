import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type CancelInstructionArgs = {
    buyerPrice: beet.bignum;
    tokenSize: beet.bignum;
};
export declare const cancelStruct: beet.BeetArgsStruct<CancelInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type CancelInstructionAccounts = {
    wallet: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    tokenMint: web3.PublicKey;
    authority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    tradeState: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const cancelInstructionDiscriminator: number[];
export declare function createCancelInstruction(accounts: CancelInstructionAccounts, args: CancelInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
