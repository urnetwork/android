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
exports.bidReceiptBeet = exports.BidReceipt = exports.bidReceiptDiscriminator = void 0;
const web3 = __importStar(require("@solana/web3.js"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const beetSolana = __importStar(require("@metaplex-foundation/beet-solana"));
exports.bidReceiptDiscriminator = [186, 150, 141, 135, 59, 122, 39, 99];
class BidReceipt {
    constructor(tradeState, bookkeeper, auctionHouse, buyer, metadata, tokenAccount, purchaseReceipt, price, tokenSize, bump, tradeStateBump, createdAt, canceledAt) {
        this.tradeState = tradeState;
        this.bookkeeper = bookkeeper;
        this.auctionHouse = auctionHouse;
        this.buyer = buyer;
        this.metadata = metadata;
        this.tokenAccount = tokenAccount;
        this.purchaseReceipt = purchaseReceipt;
        this.price = price;
        this.tokenSize = tokenSize;
        this.bump = bump;
        this.tradeStateBump = tradeStateBump;
        this.createdAt = createdAt;
        this.canceledAt = canceledAt;
    }
    static fromArgs(args) {
        return new BidReceipt(args.tradeState, args.bookkeeper, args.auctionHouse, args.buyer, args.metadata, args.tokenAccount, args.purchaseReceipt, args.price, args.tokenSize, args.bump, args.tradeStateBump, args.createdAt, args.canceledAt);
    }
    static fromAccountInfo(accountInfo, offset = 0) {
        return BidReceipt.deserialize(accountInfo.data, offset);
    }
    static async fromAccountAddress(connection, address) {
        const accountInfo = await connection.getAccountInfo(address);
        if (accountInfo == null) {
            throw new Error(`Unable to find BidReceipt account at ${address}`);
        }
        return BidReceipt.fromAccountInfo(accountInfo, 0)[0];
    }
    static gpaBuilder(programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
        return beetSolana.GpaBuilder.fromStruct(programId, exports.bidReceiptBeet);
    }
    static deserialize(buf, offset = 0) {
        return exports.bidReceiptBeet.deserialize(buf, offset);
    }
    serialize() {
        return exports.bidReceiptBeet.serialize({
            accountDiscriminator: exports.bidReceiptDiscriminator,
            ...this,
        });
    }
    static byteSize(args) {
        const instance = BidReceipt.fromArgs(args);
        return exports.bidReceiptBeet.toFixedFromValue({
            accountDiscriminator: exports.bidReceiptDiscriminator,
            ...instance,
        }).byteSize;
    }
    static async getMinimumBalanceForRentExemption(args, connection, commitment) {
        return connection.getMinimumBalanceForRentExemption(BidReceipt.byteSize(args), commitment);
    }
    pretty() {
        return {
            tradeState: this.tradeState.toBase58(),
            bookkeeper: this.bookkeeper.toBase58(),
            auctionHouse: this.auctionHouse.toBase58(),
            buyer: this.buyer.toBase58(),
            metadata: this.metadata.toBase58(),
            tokenAccount: this.tokenAccount,
            purchaseReceipt: this.purchaseReceipt,
            price: (() => {
                const x = this.price;
                if (typeof x.toNumber === 'function') {
                    try {
                        return x.toNumber();
                    }
                    catch (_) {
                        return x;
                    }
                }
                return x;
            })(),
            tokenSize: (() => {
                const x = this.tokenSize;
                if (typeof x.toNumber === 'function') {
                    try {
                        return x.toNumber();
                    }
                    catch (_) {
                        return x;
                    }
                }
                return x;
            })(),
            bump: this.bump,
            tradeStateBump: this.tradeStateBump,
            createdAt: (() => {
                const x = this.createdAt;
                if (typeof x.toNumber === 'function') {
                    try {
                        return x.toNumber();
                    }
                    catch (_) {
                        return x;
                    }
                }
                return x;
            })(),
            canceledAt: this.canceledAt,
        };
    }
}
exports.BidReceipt = BidReceipt;
exports.bidReceiptBeet = new beet.FixableBeetStruct([
    ['accountDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['tradeState', beetSolana.publicKey],
    ['bookkeeper', beetSolana.publicKey],
    ['auctionHouse', beetSolana.publicKey],
    ['buyer', beetSolana.publicKey],
    ['metadata', beetSolana.publicKey],
    ['tokenAccount', beet.coption(beetSolana.publicKey)],
    ['purchaseReceipt', beet.coption(beetSolana.publicKey)],
    ['price', beet.u64],
    ['tokenSize', beet.u64],
    ['bump', beet.u8],
    ['tradeStateBump', beet.u8],
    ['createdAt', beet.i64],
    ['canceledAt', beet.coption(beet.i64)],
], BidReceipt.fromArgs, 'BidReceipt');
//# sourceMappingURL=BidReceipt.js.map