import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const ThawDelegatedAccountStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type ThawDelegatedAccountInstructionAccounts = {
    delegate: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    edition: web3.PublicKey;
    mint: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
};
export declare const thawDelegatedAccountInstructionDiscriminator = 27;
export declare function createThawDelegatedAccountInstruction(accounts: ThawDelegatedAccountInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
