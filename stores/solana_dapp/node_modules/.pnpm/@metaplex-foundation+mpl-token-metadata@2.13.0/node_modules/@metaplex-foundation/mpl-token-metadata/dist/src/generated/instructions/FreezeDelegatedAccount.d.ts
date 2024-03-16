import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const FreezeDelegatedAccountStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type FreezeDelegatedAccountInstructionAccounts = {
    delegate: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    edition: web3.PublicKey;
    mint: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
};
export declare const freezeDelegatedAccountInstructionDiscriminator = 26;
export declare function createFreezeDelegatedAccountInstruction(accounts: FreezeDelegatedAccountInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
