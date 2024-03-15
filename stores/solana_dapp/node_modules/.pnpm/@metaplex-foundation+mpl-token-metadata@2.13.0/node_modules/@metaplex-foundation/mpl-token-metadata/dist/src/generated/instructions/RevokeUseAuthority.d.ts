import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const RevokeUseAuthorityStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type RevokeUseAuthorityInstructionAccounts = {
    useAuthorityRecord: web3.PublicKey;
    owner: web3.PublicKey;
    user: web3.PublicKey;
    ownerTokenAccount: web3.PublicKey;
    mint: web3.PublicKey;
    metadata: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const revokeUseAuthorityInstructionDiscriminator = 21;
export declare function createRevokeUseAuthorityInstruction(accounts: RevokeUseAuthorityInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
