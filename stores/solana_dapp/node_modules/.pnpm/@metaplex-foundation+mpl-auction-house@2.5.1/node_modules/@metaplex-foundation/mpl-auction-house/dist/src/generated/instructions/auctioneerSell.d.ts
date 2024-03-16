import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type AuctioneerSellInstructionArgs = {
    tradeStateBump: number;
    freeTradeStateBump: number;
    programAsSignerBump: number;
    tokenSize: beet.bignum;
};
export declare const auctioneerSellStruct: beet.BeetArgsStruct<AuctioneerSellInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type AuctioneerSellInstructionAccounts = {
    wallet: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    metadata: web3.PublicKey;
    authority: web3.PublicKey;
    auctioneerAuthority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    sellerTradeState: web3.PublicKey;
    freeSellerTradeState: web3.PublicKey;
    ahAuctioneerPda: web3.PublicKey;
    programAsSigner: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const auctioneerSellInstructionDiscriminator: number[];
export declare function createAuctioneerSellInstruction(accounts: AuctioneerSellInstructionAccounts, args: AuctioneerSellInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
