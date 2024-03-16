import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const cancelBidReceiptStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number[];
}>;
export declare type CancelBidReceiptInstructionAccounts = {
    receipt: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    instruction: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const cancelBidReceiptInstructionDiscriminator: number[];
export declare function createCancelBidReceiptInstruction(accounts: CancelBidReceiptInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
