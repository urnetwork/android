import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const ApproveCollectionAuthorityStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type ApproveCollectionAuthorityInstructionAccounts = {
    collectionAuthorityRecord: web3.PublicKey;
    newCollectionAuthority: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    metadata: web3.PublicKey;
    mint: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const approveCollectionAuthorityInstructionDiscriminator = 23;
export declare function createApproveCollectionAuthorityInstruction(accounts: ApproveCollectionAuthorityInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
