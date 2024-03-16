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
exports.createUnverifySizedCollectionItemInstruction = exports.unverifySizedCollectionItemInstructionDiscriminator = exports.UnverifySizedCollectionItemStruct = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
exports.UnverifySizedCollectionItemStruct = new beet.BeetArgsStruct([['instructionDiscriminator', beet.u8]], 'UnverifySizedCollectionItemInstructionArgs');
exports.unverifySizedCollectionItemInstructionDiscriminator = 31;
function createUnverifySizedCollectionItemInstruction(accounts, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    const [data] = exports.UnverifySizedCollectionItemStruct.serialize({
        instructionDiscriminator: exports.unverifySizedCollectionItemInstructionDiscriminator,
    });
    const keys = [
        {
            pubkey: accounts.metadata,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.collectionAuthority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.payer,
            isWritable: true,
            isSigner: true,
        },
        {
            pubkey: accounts.collectionMint,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.collection,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.collectionMasterEditionAccount,
            isWritable: false,
            isSigner: false,
        },
    ];
    if (accounts.collectionAuthorityRecord != null) {
        keys.push({
            pubkey: accounts.collectionAuthorityRecord,
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
exports.createUnverifySizedCollectionItemInstruction = createUnverifySizedCollectionItemInstruction;
//# sourceMappingURL=UnverifySizedCollectionItem.js.map