import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const SetTokenStandardStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type SetTokenStandardInstructionAccounts = {
    metadata: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    mint: web3.PublicKey;
    edition?: web3.PublicKey;
};
export declare const setTokenStandardInstructionDiscriminator = 35;
export declare function createSetTokenStandardInstruction(accounts: SetTokenStandardInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
