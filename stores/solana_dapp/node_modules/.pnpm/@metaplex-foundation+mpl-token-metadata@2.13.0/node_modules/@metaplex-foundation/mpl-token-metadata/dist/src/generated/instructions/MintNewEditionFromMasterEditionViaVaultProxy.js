"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.createMintNewEditionFromMasterEditionViaVaultProxyInstruction = exports.mintNewEditionFromMasterEditionViaVaultProxyInstructionDiscriminator = exports.MintNewEditionFromMasterEditionViaVaultProxyStruct = void 0;
const splToken = __importStar(require("@solana/spl-token"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
const MintNewEditionFromMasterEditionViaTokenArgs_1 = require("../types/MintNewEditionFromMasterEditionViaTokenArgs");
exports.MintNewEditionFromMasterEditionViaVaultProxyStruct = new beet.BeetArgsStruct([
    ['instructionDiscriminator', beet.u8],
    [
        'mintNewEditionFromMasterEditionViaTokenArgs',
        MintNewEditionFromMasterEditionViaTokenArgs_1.mintNewEditionFromMasterEditionViaTokenArgsBeet,
    ],
], 'MintNewEditionFromMasterEditionViaVaultProxyInstructionArgs');
exports.mintNewEditionFromMasterEditionViaVaultProxyInstructionDiscriminator = 13;
function createMintNewEditionFromMasterEditionViaVaultProxyInstruction(accounts, args, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    var _a, _b;
    const [data] = exports.MintNewEditionFromMasterEditionViaVaultProxyStruct.serialize({
        instructionDiscriminator: exports.mintNewEditionFromMasterEditionViaVaultProxyInstructionDiscriminator,
        ...args,
    });
    const keys = [
        {
            pubkey: accounts.newMetadata,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.newEdition,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.masterEdition,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.newMint,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.editionMarkPda,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.newMintAuthority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.payer,
            isWritable: true,
            isSigner: true,
        },
        {
            pubkey: accounts.vaultAuthority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.safetyDepositStore,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.safetyDepositBox,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.vault,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.newMetadataUpdateAuthority,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.metadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_a = accounts.tokenProgram) !== null && _a !== void 0 ? _a : splToken.TOKEN_PROGRAM_ID,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.tokenVaultProgram,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_b = accounts.systemProgram) !== null && _b !== void 0 ? _b : web3.SystemProgram.programId,
            isWritable: false,
            isSigner: false,
        },
    ];
    if (accounts.rent != null) {
        keys.push({
            pubkey: accounts.rent,
            isWritable: false,
            isSigner: false,
        });
    }
    const ix = new web3.TransactionInstruction({
        programId,
        keys,
        data,
    });
    return ix;
}
exports.createMintNewEditionFromMasterEditionViaVaultProxyInstruction = createMintNewEditionFromMasterEditionViaVaultProxyInstruction;
//# sourceMappingURL=MintNewEditionFromMasterEditionViaVaultProxy.js.map