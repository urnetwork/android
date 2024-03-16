import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const cancelListingReceiptStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number[];
}>;
export declare type CancelListingReceiptInstructionAccounts = {
    receipt: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    instruction: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const cancelListingReceiptInstructionDiscriminator: number[];
export declare function createCancelListingReceiptInstruction(accounts: CancelListingReceiptInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
