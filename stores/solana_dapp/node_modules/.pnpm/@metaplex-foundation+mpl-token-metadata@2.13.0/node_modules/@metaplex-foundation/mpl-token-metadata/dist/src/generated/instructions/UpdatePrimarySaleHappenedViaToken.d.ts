import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const UpdatePrimarySaleHappenedViaTokenStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type UpdatePrimarySaleHappenedViaTokenInstructionAccounts = {
    metadata: web3.PublicKey;
    owner: web3.PublicKey;
    token: web3.PublicKey;
};
export declare const updatePrimarySaleHappenedViaTokenInstructionDiscriminator = 4;
export declare function createUpdatePrimarySaleHappenedViaTokenInstruction(accounts: UpdatePrimarySaleHappenedViaTokenInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
