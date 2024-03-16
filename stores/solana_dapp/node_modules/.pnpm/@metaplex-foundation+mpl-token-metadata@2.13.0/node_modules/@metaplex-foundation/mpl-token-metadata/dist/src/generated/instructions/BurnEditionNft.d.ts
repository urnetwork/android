import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const BurnEditionNftStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type BurnEditionNftInstructionAccounts = {
    metadata: web3.PublicKey;
    owner: web3.PublicKey;
    printEditionMint: web3.PublicKey;
    masterEditionMint: web3.PublicKey;
    printEditionTokenAccount: web3.PublicKey;
    masterEditionTokenAccount: web3.PublicKey;
    masterEditionAccount: web3.PublicKey;
    printEditionAccount: web3.PublicKey;
    editionMarkerAccount: web3.PublicKey;
    splTokenProgram: web3.PublicKey;
};
export declare const burnEditionNftInstructionDiscriminator = 37;
export declare function createBurnEditionNftInstruction(accounts: BurnEditionNftInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
