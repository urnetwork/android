import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type BuyInstructionArgs = {
    tradeStateBump: number;
    escrowPaymentBump: number;
    buyerPrice: beet.bignum;
    tokenSize: beet.bignum;
};
export declare const buyStruct: beet.BeetArgsStruct<BuyInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type BuyInstructionAccounts = {
    wallet: web3.PublicKey;
    paymentAccount: web3.PublicKey;
    transferAuthority: web3.PublicKey;
    treasuryMint: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    metadata: web3.PublicKey;
    escrowPaymentAccount: web3.PublicKey;
    authority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    auctionHouseFeeAccount: web3.PublicKey;
    buyerTradeState: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const buyInstructionDiscriminator: number[];
export declare function createBuyInstruction(accounts: BuyInstructionAccounts, args: BuyInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
