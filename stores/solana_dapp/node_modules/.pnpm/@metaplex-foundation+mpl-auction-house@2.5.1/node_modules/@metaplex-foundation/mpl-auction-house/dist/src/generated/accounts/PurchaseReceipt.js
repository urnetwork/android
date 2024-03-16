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
exports.purchaseReceiptBeet = exports.PurchaseReceipt = exports.purchaseReceiptDiscriminator = void 0;
const web3 = __importStar(require("@solana/web3.js"));
const beet = __importStar(require("@metaplex-foundation/beet"));
const beetSolana = __importStar(require("@metaplex-foundation/beet-solana"));
exports.purchaseReceiptDiscriminator = [79, 127, 222, 137, 154, 131, 150, 134];
class PurchaseReceipt {
    constructor(bookkeeper, buyer, seller, auctionHouse, metadata, tokenSize, price, bump, createdAt) {
        this.bookkeeper = bookkeeper;
        this.buyer = buyer;
        this.seller = seller;
        this.auctionHouse = auctionHouse;
        this.metadata = metadata;
        this.tokenSize = tokenSize;
        this.price = price;
        this.bump = bump;
        this.createdAt = createdAt;
    }
    static fromArgs(args) {
        return new PurchaseReceipt(args.bookkeeper, args.buyer, args.seller, args.auctionHouse, args.metadata, args.tokenSize, args.price, args.bump, args.createdAt);
    }
    static fromAccountInfo(accountInfo, offset = 0) {
        return PurchaseReceipt.deserialize(accountInfo.data, offset);
    }
    static async fromAccountAddress(connection, address) {
        const accountInfo = await connection.getAccountInfo(address);
        if (accountInfo == null) {
            throw new Error(`Unable to find PurchaseReceipt account at ${address}`);
        }
        return PurchaseReceipt.fromAccountInfo(accountInfo, 0)[0];
    }
    static gpaBuilder(programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
        return beetSolana.GpaBuilder.fromStruct(programId, exports.purchaseReceiptBeet);
    }
    static deserialize(buf, offset = 0) {
        return exports.purchaseReceiptBeet.deserialize(buf, offset);
    }
    serialize() {
        return exports.purchaseReceiptBeet.serialize({
            accountDiscriminator: exports.purchaseReceiptDiscriminator,
            ...this,
        });
    }
    static get byteSize() {
        return exports.purchaseReceiptBeet.byteSize;
    }
    static async getMinimumBalanceForRentExemption(connection, commitment) {
        return connection.getMinimumBalanceForRentExemption(PurchaseReceipt.byteSize, commitment);
    }
    static hasCorrectByteSize(buf, offset = 0) {
        return buf.byteLength - offset === PurchaseReceipt.byteSize;
    }
    pretty() {
        return {
            bookkeeper: this.bookkeeper.toBase58(),
            buyer: this.buyer.toBase58(),
            seller: this.seller.toBase58(),
            auctionHouse: this.auctionHouse.toBase58(),
            metadata: this.metadata.toBase58(),
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
            bump: this.bump,
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
        };
    }
}
exports.PurchaseReceipt = PurchaseReceipt;
exports.purchaseReceiptBeet = new beet.BeetStruct([
    ['accountDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['bookkeeper', beetSolana.publicKey],
    ['buyer', beetSolana.publicKey],
    ['seller', beetSolana.publicKey],
    ['auctionHouse', beetSolana.publicKey],
    ['metadata', beetSolana.publicKey],
    ['tokenSize', beet.u64],
    ['price', beet.u64],
    ['bump', beet.u8],
    ['createdAt', beet.i64],
], PurchaseReceipt.fromArgs, 'PurchaseReceipt');
//# sourceMappingURL=PurchaseReceipt.js.map