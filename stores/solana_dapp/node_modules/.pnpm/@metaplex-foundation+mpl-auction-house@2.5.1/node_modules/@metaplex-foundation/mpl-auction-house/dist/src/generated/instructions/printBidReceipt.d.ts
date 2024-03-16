import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type PrintBidReceiptInstructionArgs = {
    receiptBump: number;
};
export declare const printBidReceiptStruct: beet.BeetArgsStruct<PrintBidReceiptInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type PrintBidReceiptInstructionAccounts = {
    receipt: web3.PublicKey;
    bookkeeper: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    instruction: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const printBidReceiptInstructionDiscriminator: number[];
export declare function createPrintBidReceiptInstruction(accounts: PrintBidReceiptInstructionAccounts, args: PrintBidReceiptInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
