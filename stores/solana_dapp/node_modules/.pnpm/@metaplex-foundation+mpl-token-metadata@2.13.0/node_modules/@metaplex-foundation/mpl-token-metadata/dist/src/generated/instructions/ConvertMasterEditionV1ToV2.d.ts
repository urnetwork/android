import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const ConvertMasterEditionV1ToV2Struct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type ConvertMasterEditionV1ToV2InstructionAccounts = {
    masterEdition: web3.PublicKey;
    oneTimeAuth: web3.PublicKey;
    printingMint: web3.PublicKey;
};
export declare const convertMasterEditionV1ToV2InstructionDiscriminator = 12;
export declare function createConvertMasterEditionV1ToV2Instruction(accounts: ConvertMasterEditionV1ToV2InstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
