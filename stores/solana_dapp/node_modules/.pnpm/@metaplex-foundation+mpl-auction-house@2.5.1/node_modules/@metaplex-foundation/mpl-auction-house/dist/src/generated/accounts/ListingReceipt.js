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
exports.listingReceiptBeet = exports.ListingReceipt = exports.listingReceiptDiscriminator = void 0;
const web3 = __importStar(require("@solana/web3.js"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const beetSolana = __importStar(require("@metaplex-foundation/beet-solana"));
exports.listingReceiptDiscriminator = [240, 71, 225, 94, 200, 75, 84, 231];
class ListingReceipt {
    constructor(tradeState, bookkeeper, auctionHouse, seller, metadata, purchaseReceipt, price, tokenSize, bump, tradeStateBump, createdAt, canceledAt) {
        this.tradeState = tradeState;
        this.bookkeeper = bookkeeper;
        this.auctionHouse = auctionHouse;
        this.seller = seller;
        this.metadata = metadata;
        this.purchaseReceipt = purchaseReceipt;
        this.price = price;
        this.tokenSize = tokenSize;
        this.bump = bump;
        this.tradeStateBump = tradeStateBump;
        this.createdAt = createdAt;
        this.canceledAt = canceledAt;
    }
    static fromArgs(args) {
        return new ListingReceipt(args.tradeState, args.bookkeeper, args.auctionHouse, args.seller, args.metadata, args.purchaseReceipt, args.price, args.tokenSize, args.bump, args.tradeStateBump, args.createdAt, args.canceledAt);
    }
    static fromAccountInfo(accountInfo, offset = 0) {
        return ListingReceipt.deserialize(accountInfo.data, offset);
    }
    static async fromAccountAddress(connection, address) {
        const accountInfo = await connection.getAccountInfo(address);
        if (accountInfo == null) {
            throw new Error(`Unable to find ListingReceipt account at ${address}`);
        }
        return ListingReceipt.fromAccountInfo(accountInfo, 0)[0];
    }
    static gpaBuilder(programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
        return beetSolana.GpaBuilder.fromStruct(programId, exports.listingReceiptBeet);
    }
    static deserialize(buf, offset = 0) {
        return exports.listingReceiptBeet.deserialize(buf, offset);
    }
    serialize() {
        return exports.listingReceiptBeet.serialize({
            accountDiscriminator: exports.listingReceiptDiscriminator,
            ...this,
        });
    }
    static byteSize(args) {
        const instance = ListingReceipt.fromArgs(args);
        return exports.listingReceiptBeet.toFixedFromValue({
            accountDiscriminator: exports.listingReceiptDiscriminator,
            ...instance,
        }).byteSize;
    }
    static async getMinimumBalanceForRentExemption(args, connection, commitment) {
        return connection.getMinimumBalanceForRentExemption(ListingReceipt.byteSize(args), commitment);
    }
    pretty() {
        return {
            tradeState: this.tradeState.toBase58(),
            bookkeeper: this.bookkeeper.toBase58(),
            auctionHouse: this.auctionHouse.toBase58(),
            seller: this.seller.toBase58(),
            metadata: this.metadata.toBase58(),
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
exports.ListingReceipt = ListingReceipt;
exports.listingReceiptBeet = new beet.FixableBeetStruct([
    ['accountDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['tradeState', beetSolana.publicKey],
    ['bookkeeper', beetSolana.publicKey],
    ['auctionHouse', beetSolana.publicKey],
    ['seller', beetSolana.publicKey],
    ['metadata', beetSolana.publicKey],
    ['purchaseReceipt', beet.coption(beetSolana.publicKey)],
    ['price', beet.u64],
    ['tokenSize', beet.u64],
    ['bump', beet.u8],
    ['tradeStateBump', beet.u8],
    ['createdAt', beet.i64],
    ['canceledAt', beet.coption(beet.i64)],
], ListingReceipt.fromArgs, 'ListingReceipt');
//# sourceMappingURL=ListingReceipt.js.map