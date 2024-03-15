import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare type PrintListingReceiptInstructionArgs = {
    receiptBump: number;
};
export declare const printListingReceiptStruct: beet.BeetArgsStruct<PrintListingReceiptInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type PrintListingReceiptInstructionAccounts = {
    receipt: web3.PublicKey;
    bookkeeper: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    instruction: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const printListingReceiptInstructionDiscriminator: number[];
export declare function createPrintListingReceiptInstruction(accounts: PrintListingReceiptInstructionAccounts, args: PrintListingReceiptInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
