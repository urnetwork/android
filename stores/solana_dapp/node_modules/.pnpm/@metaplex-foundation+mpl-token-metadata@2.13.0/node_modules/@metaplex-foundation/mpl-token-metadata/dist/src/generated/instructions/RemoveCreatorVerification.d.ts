import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const RemoveCreatorVerificationStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type RemoveCreatorVerificationInstructionAccounts = {
    metadata: web3.PublicKey;
    creator: web3.PublicKey;
};
export declare const removeCreatorVerificationInstructionDiscriminator = 28;
export declare function createRemoveCreatorVerificationInstruction(accounts: RemoveCreatorVerificationInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
