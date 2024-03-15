import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type AuctioneerCancelInstructionArgs = {
    buyerPrice: beet.bignum;
    tokenSize: beet.bignum;
};
export declare const auctioneerCancelStruct: beet.BeetArgsStruct<AuctioneerCancelInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type AuctioneerCancelInstructionAccounts = {
    wallet: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    tokenMint: web3.PublicKey;
    authority: web3.PublicKey;
    auctioneerAuthority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    tradeState: web3.PublicKey;
    ahAuctioneerPda: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const auctioneerCancelInstructionDiscriminator: number[];
export declare function createAuctioneerCancelInstruction(accounts: AuctioneerCancelInstructionAccounts, args: AuctioneerCancelInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
