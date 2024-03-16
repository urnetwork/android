import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const DeprecatedMintNewEditionFromMasterEditionViaPrintingTokenStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type DeprecatedMintNewEditionFromMasterEditionViaPrintingTokenInstructionAccounts = {
    metadata: web3.PublicKey;
    edition: web3.PublicKey;
    masterEdition: web3.PublicKey;
    mint: web3.PublicKey;
    mintAuthority: web3.PublicKey;
    printingMint: web3.PublicKey;
    masterTokenAccount: web3.PublicKey;
    editionMarker: web3.PublicKey;
    burnAuthority: web3.PublicKey;
    payer: web3.PublicKey;
    masterUpdateAuthority: web3.PublicKey;
    masterMetadata: web3.PublicKey;
    tokenProgram?: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
    reservationList?: web3.PublicKey;
};
export declare const deprecatedMintNewEditionFromMasterEditionViaPrintingTokenInstructionDiscriminator = 3;
export declare function createDeprecatedMintNewEditionFromMasterEditionViaPrintingTokenInstruction(accounts: DeprecatedMintNewEditionFromMasterEditionViaPrintingTokenInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
