import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { MintNewEditionFromMasterEditionViaTokenArgs } from '../types/MintNewEditionFromMasterEditionViaTokenArgs';
export type MintNewEditionFromMasterEditionViaVaultProxyInstructionArgs = {
    mintNewEditionFromMasterEditionViaTokenArgs: MintNewEditionFromMasterEditionViaTokenArgs;
};
export declare const MintNewEditionFromMasterEditionViaVaultProxyStruct: beet.BeetArgsStruct<MintNewEditionFromMasterEditionViaVaultProxyInstructionArgs & {
    instructionDiscriminator: number;
}>;
export type MintNewEditionFromMasterEditionViaVaultProxyInstructionAccounts = {
    newMetadata: web3.PublicKey;
    newEdition: web3.PublicKey;
    masterEdition: web3.PublicKey;
    newMint: web3.PublicKey;
    editionMarkPda: web3.PublicKey;
    newMintAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    vaultAuthority: web3.PublicKey;
    safetyDepositStore: web3.PublicKey;
    safetyDepositBox: web3.PublicKey;
    vault: web3.PublicKey;
    newMetadataUpdateAuthority: web3.PublicKey;
    metadata: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    tokenVaultProgram: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const mintNewEditionFromMasterEditionViaVaultProxyInstructionDiscriminator = 13;
export declare function createMintNewEditionFromMasterEditionViaVaultProxyInstruction(accounts: MintNewEditionFromMasterEditionViaVaultProxyInstructionAccounts, args: MintNewEditionFromMasterEditionViaVaultProxyInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
