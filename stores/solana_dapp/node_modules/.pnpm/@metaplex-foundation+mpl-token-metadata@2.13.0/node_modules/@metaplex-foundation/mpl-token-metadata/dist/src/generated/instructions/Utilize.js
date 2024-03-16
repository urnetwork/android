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
exports.createUtilizeInstruction = exports.utilizeInstructionDiscriminator = exports.UtilizeStruct = void 0;
const splToken = __importStar(require("@solana/spl-token"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
const UtilizeArgs_1 = require("../types/UtilizeArgs");
exports.UtilizeStruct = new beet.BeetArgsStruct([
    ['instructionDiscriminator', beet.u8],
    ['utilizeArgs', UtilizeArgs_1.utilizeArgsBeet],
], 'UtilizeInstructionArgs');
exports.utilizeInstructionDiscriminator = 19;
function createUtilizeInstruction(accounts, args, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    var _a, _b, _c, _d;
    const [data] = exports.UtilizeStruct.serialize({
        instructionDiscriminator: exports.utilizeInstructionDiscriminator,
        ...args,
    });
    const keys = [
        {
            pubkey: accounts.metadata,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.tokenAccount,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.mint,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.useAuthority,
            isWritable: true,
            isSigner: true,
        },
        {
            pubkey: accounts.owner,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_a = accounts.tokenProgram) !== null && _a !== void 0 ? _a : splToken.TOKEN_PROGRAM_ID,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_b = accounts.ataProgram) !== null && _b !== void 0 ? _b : splToken.ASSOCIATED_TOKEN_PROGRAM_ID,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_c = accounts.systemProgram) !== null && _c !== void 0 ? _c : web3.SystemProgram.programId,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_d = accounts.rent) !== null && _d !== void 0 ? _d : web3.SYSVAR_RENT_PUBKEY,
            isWritable: false,
            isSigner: false,
        },
    ];
    if (accounts.useAuthorityRecord != null) {
        keys.push({
            pubkey: accounts.useAuthorityRecord,
            isWritable: true,
            isSigner: false,
        });
    }
    if (accounts.burner != null) {
        if (accounts.useAuthorityRecord == null) {
            throw new Error("When providing 'burner' then 'accounts.useAuthorityRecord' need(s) to be provided as well.");
        }
        keys.push({
            pubkey: accounts.burner,
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
exports.createUtilizeInstruction = createUtilizeInstruction;
//# sourceMappingURL=Utilize.js.map