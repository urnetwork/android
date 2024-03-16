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
exports.createRevokeCollectionAuthorityInstruction = exports.revokeCollectionAuthorityInstructionDiscriminator = exports.RevokeCollectionAuthorityStruct = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
exports.RevokeCollectionAuthorityStruct = new beet.BeetArgsStruct([['instructionDiscriminator', beet.u8]], 'RevokeCollectionAuthorityInstructionArgs');
exports.revokeCollectionAuthorityInstructionDiscriminator = 24;
function createRevokeCollectionAuthorityInstruction(accounts, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    const [data] = exports.RevokeCollectionAuthorityStruct.serialize({
        instructionDiscriminator: exports.revokeCollectionAuthorityInstructionDiscriminator,
    });
    const keys = [
        {
            pubkey: accounts.collectionAuthorityRecord,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.delegateAuthority,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.revokeAuthority,
            isWritable: true,
            isSigner: true,
        },
        {
            pubkey: accounts.metadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.mint,
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
exports.createRevokeCollectionAuthorityInstruction = createRevokeCollectionAuthorityInstruction;
//# sourceMappingURL=RevokeCollectionAuthority.js.map