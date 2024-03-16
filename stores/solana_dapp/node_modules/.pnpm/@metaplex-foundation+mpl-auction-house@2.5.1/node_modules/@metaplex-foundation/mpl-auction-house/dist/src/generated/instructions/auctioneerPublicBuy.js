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
exports.createAuctioneerPublicBuyInstruction = exports.auctioneerPublicBuyInstructionDiscriminator = exports.auctioneerPublicBuyStruct = void 0;
const splToken = __importStar(require("@solana/spl-token"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const web3 = __importStar(require("@solana/web3.js"));
exports.auctioneerPublicBuyStruct = new beet.BeetArgsStruct([
    ['instructionDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['tradeStateBump', beet.u8],
    ['escrowPaymentBump', beet.u8],
    ['buyerPrice', beet.u64],
    ['tokenSize', beet.u64],
], 'AuctioneerPublicBuyInstructionArgs');
exports.auctioneerPublicBuyInstructionDiscriminator = [221, 239, 99, 240, 86, 46, 213, 126];
function createAuctioneerPublicBuyInstruction(accounts, args, programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
    var _a, _b, _c;
    const [data] = exports.auctioneerPublicBuyStruct.serialize({
        instructionDiscriminator: exports.auctioneerPublicBuyInstructionDiscriminator,
        ...args,
    });
    const keys = [
        {
            pubkey: accounts.wallet,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.paymentAccount,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.transferAuthority,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.treasuryMint,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.tokenAccount,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.metadata,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.escrowPaymentAccount,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.authority,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.auctioneerAuthority,
            isWritable: false,
            isSigner: true,
        },
        {
            pubkey: accounts.auctionHouse,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: accounts.auctionHouseFeeAccount,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.buyerTradeState,
            isWritable: true,
            isSigner: false,
        },
        {
            pubkey: accounts.ahAuctioneerPda,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_a = accounts.tokenProgram) !== null && _a !== void 0 ? _a : splToken.TOKEN_PROGRAM_ID,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_b = accounts.systemProgram) !== null && _b !== void 0 ? _b : web3.SystemProgram.programId,
            isWritable: false,
            isSigner: false,
        },
        {
            pubkey: (_c = accounts.rent) !== null && _c !== void 0 ? _c : web3.SYSVAR_RENT_PUBKEY,
            isWritable: false,
            isSigner: false,
        },
    ];
    if (accounts.anchorRemainingAccounts != null) {
        for (const acc of accounts.anchorRemainingAccounts) {
            keys.push(acc);
        }
    }
    const ix = new web3.TransactionInstruction({
        programId,
        keys,
        data,
    });
    return ix;
}
exports.createAuctioneerPublicBuyInstruction = createAuctioneerPublicBuyInstruction;
//# sourceMappingURL=auctioneerPublicBuy.js.map