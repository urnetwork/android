import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type PrintPurchaseReceiptInstructionArgs = {
    purchaseReceiptBump: number;
};
export declare const printPurchaseReceiptStruct: beet.BeetArgsStruct<PrintPurchaseReceiptInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type PrintPurchaseReceiptInstructionAccounts = {
    purchaseReceipt: web3.PublicKey;
    listingReceipt: web3.PublicKey;
    bidReceipt: web3.PublicKey;
    bookkeeper: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    instruction: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const printPurchaseReceiptInstructionDiscriminator: number[];
export declare function createPrintPurchaseReceiptInstruction(accounts: PrintPurchaseReceiptInstructionAccounts, args: PrintPurchaseReceiptInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
