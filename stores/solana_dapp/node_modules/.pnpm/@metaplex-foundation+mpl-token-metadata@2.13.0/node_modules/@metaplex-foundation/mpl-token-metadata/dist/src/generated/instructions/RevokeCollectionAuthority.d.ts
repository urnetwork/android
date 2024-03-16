import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const RevokeCollectionAuthorityStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type RevokeCollectionAuthorityInstructionAccounts = {
    collectionAuthorityRecord: web3.PublicKey;
    delegateAuthority: web3.PublicKey;
    revokeAuthority: web3.PublicKey;
    metadata: web3.PublicKey;
    mint: web3.PublicKey;
};
export declare const revokeCollectionAuthorityInstructionDiscriminator = 24;
export declare function createRevokeCollectionAuthorityInstruction(accounts: RevokeCollectionAuthorityInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
