import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { MintNewEditionFromMasterEditionViaTokenArgs } from '../types/MintNewEditionFromMasterEditionViaTokenArgs';
export type MintNewEditionFromMasterEditionViaTokenInstructionArgs = {
    mintNewEditionFromMasterEditionViaTokenArgs: MintNewEditionFromMasterEditionViaTokenArgs;
};
export declare const MintNewEditionFromMasterEditionViaTokenStruct: beet.BeetArgsStruct<MintNewEditionFromMasterEditionViaTokenInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type MintNewEditionFromMasterEditionViaTokenInstructionAccounts = {
    newMetadata: web3.PublicKey;
    newEdition: web3.PublicKey;
    masterEdition: web3.PublicKey;
    newMint: web3.PublicKey;
    editionMarkPda: web3.PublicKey;
    newMintAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    tokenAccountOwner: web3.PublicKey;
    tokenAccount: web3.PublicKey;
    newMetadataUpdateAuthority: web3.PublicKey;
    metadata: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const mintNewEditionFromMasterEditionViaTokenInstructionDiscriminator = 11;
export declare function createMintNewEditionFromMasterEditionViaTokenInstruction(accounts: MintNewEditionFromMasterEditionViaTokenInstructionAccounts, args: MintNewEditionFromMasterEditionViaTokenInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
