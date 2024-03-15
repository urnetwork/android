import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const BurnNftStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type BurnNftInstructionAccounts = {
    metadata: web3.PublicKey;
    owner: web3.PublicKey;
    mint: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    masterEditionAccount: web3.PublicKey;
    splTokenProgram: web3.PublicKey;
    collectionMetadata?: web3.PublicKey;
};
export declare const burnNftInstructionDiscriminator = 29;
export declare function createBurnNftInstruction(accounts: BurnNftInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
