import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type SellInstructionArgs = {
    tradeStateBump: number;
    freeTradeStateBump: number;
    programAsSignerBump: number;
    buyerPrice: beet.bignum;
    tokenSize: beet.bignum;
};
export declare const sellStruct: beet.BeetArgsStruct<SellInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type SellInstructionAccounts = {
    wallet: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    metadata: web3.PublicKey;
    authority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    sellerTradeState: web3.PublicKey;
    freeSellerTradeState: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    programAsSigner: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const sellInstructionDiscriminator: number[];
export declare function createSellInstruction(accounts: SellInstructionAccounts, args: SellInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
