import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { ApproveUseAuthorityArgs } from '../types/ApproveUseAuthorityArgs';
export type ApproveUseAuthorityInstructionArgs = {
    approveUseAuthorityArgs: ApproveUseAuthorityArgs;
};
export declare const ApproveUseAuthorityStruct: beet.BeetArgsStruct<ApproveUseAuthorityInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type ApproveUseAuthorityInstructionAccounts = {
    useAuthorityRecord: web3.PublicKey;
    owner: web3.PublicKey;
    payer: web3.PublicKey;
    user: web3.PublicKey;
    ownerTokenAccount: web3.PublicKey;
    metadata: web3.PublicKey;
    mint: web3.PublicKey;
    burner: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const approveUseAuthorityInstructionDiscriminator = 20;
export declare function createApproveUseAuthorityInstruction(accounts: ApproveUseAuthorityInstructionAccounts, args: ApproveUseAuthorityInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
