import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { UtilizeArgs } from '../types/UtilizeArgs';
export type UtilizeInstructionArgs = {
    utilizeArgs: UtilizeArgs;
};
export declare const UtilizeStruct: beet.BeetArgsStruct<UtilizeInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type UtilizeInstructionAccounts = {
    metadata: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    mint: web3.PublicKey;
    useAuthority: web3.PublicKey;
    owner: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    ataProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    useAuthorityRecord?: web3.PublicKey;
    burner?: web3.PublicKey;
};
export declare const utilizeInstructionDiscriminator = 19;
export declare function createUtilizeInstruction(accounts: UtilizeInstructionAccounts, args: UtilizeInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
