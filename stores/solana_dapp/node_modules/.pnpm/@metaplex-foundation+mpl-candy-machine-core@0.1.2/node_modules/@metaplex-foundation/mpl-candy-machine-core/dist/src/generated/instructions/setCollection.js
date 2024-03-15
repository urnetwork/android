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
exports.createSetCollectionInstruction = exports.setCollectionInstructionDiscriminator = exports.setCollectionStruct = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
exports.setCollectionStruct = new beet.BeetArgsStruct([['instructionDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)]], 'SetCollectionInstructionArgs');
exports.setCollectionInstructionDiscriminator = [192, 254, 206, 76, 168, 182, 59, 223];
function createSetCollectionInstruction(accounts, programId = new web3.PublicKey('CndyV3LdqHUfDLmE5naZjVN8rBZz4tqhdefbAnjHG3JR')) {
    var _a;
    const [data] = exports.setCollectionStruct.serialize({
        instructionDiscriminator: exports.setCollectionInstructionDiscriminator,
    });
    const keys = [
        {
            pubkey: accounts.candyMachine,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.authority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.authorityPda,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.payer,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.collectionMint,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.collectionMetadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.collectionAuthorityRecord,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.newCollectionUpdateAuthority,
            isWritable: true,
            isSigner: true,
        },
        {
            pubkey: accounts.newCollectionMetadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.newCollectionMint,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.newCollectionMasterEdition,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.newCollectionAuthorityRecord,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.tokenMetadataProgram,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_a = accounts.systemProgram) !== null && _a !== void 0 ? _a : web3.SystemProgram.programId,
            isWritable: false,
            isSigner: false,
        },
    ];
    const ix = new web3.TransactionInstruction({
        programId,
        keys,
        data,
    });
    return ix;
}
exports.createSetCollectionInstruction = createSetCollectionInstruction;
//# sourceMappingURL=setCollection.js.map