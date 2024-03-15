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
exports.createDeprecatedCreateReservationListInstruction = exports.deprecatedCreateReservationListInstructionDiscriminator = exports.DeprecatedCreateReservationListStruct = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
exports.DeprecatedCreateReservationListStruct = new beet.BeetArgsStruct([['instructionDiscriminator', beet.u8]], 'DeprecatedCreateReservationListInstructionArgs');
exports.deprecatedCreateReservationListInstructionDiscriminator = 6;
function createDeprecatedCreateReservationListInstruction(accounts, programId = new web3.PublicKey('metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s')) {
    var _a, _b;
    const [data] = exports.DeprecatedCreateReservationListStruct.serialize({
        instructionDiscriminator: exports.deprecatedCreateReservationListInstructionDiscriminator,
    });
    const keys = [
        {
            pubkey: accounts.reservationList,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.payer,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.updateAuthority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.masterEdition,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.resource,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.metadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_a = accounts.systemProgram) !== null && _a !== void 0 ? _a : web3.SystemProgram.programId,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_b = accounts.rent) !== null && _b !== void 0 ? _b : web3.SYSVAR_RENT_PUBKEY,
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
exports.createDeprecatedCreateReservationListInstruction = createDeprecatedCreateReservationListInstruction;
//# sourceMappingURL=DeprecatedCreateReservationList.js.map