import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const setMintAuthorityStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number[];
}>;
export declare type SetMintAuthorityInstructionAccounts = {
    candyMachine: web3.PublicKey;
    authority: web3.PublicKey;
    mintAuthority: web3.PublicKey;
};
export declare const setMintAuthorityInstructionDiscriminator: number[];
export declare function createSetMintAuthorityInstruction(accounts: SetMintAuthorityInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
