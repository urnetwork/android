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
exports.createUpdateMetadataAccountV2Instruction = exports.updateMetadataAccountV2InstructionDiscriminator = exports.UpdateMetadataAccountV2Struct = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
const UpdateMetadataAccountArgsV2_1 = require("../types/UpdateMetadataAccountArgsV2");
exports.UpdateMetadataAccountV2Struct = new beet.FixableBeetArgsStruct([
    ['instructionDiscriminator', beet.u8],
    ['updateMetadataAccountArgsV2', UpdateMetadataAccountArgsV2_1.updateMetadataAccountArgsV2Beet],
], 'UpdateMetadataAccountV2InstructionArgs');
exports.updateMetadataAccountV2InstructionDiscriminator = 15;
function createUpdateMetadataAccountV2Instruction(accounts, args, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    const [data] = exports.UpdateMetadataAccountV2Struct.serialize({
        instructionDiscriminator: exports.updateMetadataAccountV2InstructionDiscriminator,
        ...args,
    });
    const keys = [
        {
            pubkey: accounts.metadata,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.updateAuthority,
            isWritable: false,
            isSigner: true,
        },
    ];
    const ix = new web3.TransactionInstruction({
        programId,
        keys,
        data,
    });
    return ix;
}
exports.createUpdateMetadataAccountV2Instruction = createUpdateMetadataAccountV2Instruction;
//# sourceMappingURL=UpdateMetadataAccountV2.js.map