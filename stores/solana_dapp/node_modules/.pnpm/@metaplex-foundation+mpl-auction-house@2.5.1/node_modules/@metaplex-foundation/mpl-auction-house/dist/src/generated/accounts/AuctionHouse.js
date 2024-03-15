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
exports.auctionHouseBeet = exports.AuctionHouse = exports.auctionHouseDiscriminator = void 0;
const web3 = __importStar(require("@solana/web3.js"));
const beetSolana = __importStar(require("@metaplex-foundation/beet-solana"));
const beet = __importStar(require("@metaplex-foundation/beet"));
exports.auctionHouseDiscriminator = [40, 108, 215, 107, 213, 85, 245, 48];
class AuctionHouse {
    constructor(auctionHouseFeeAccount, auctionHouseTreasury, treasuryWithdrawalDestination, feeWithdrawalDestination, treasuryMint, authority, creator, bump, treasuryBump, feePayerBump, sellerFeeBasisPoints, requiresSignOff, canChangeSalePrice, escrowPaymentBump, hasAuctioneer, auctioneerAddress, scopes) {
        this.auctionHouseFeeAccount = auctionHouseFeeAccount;
        this.auctionHouseTreasury = auctionHouseTreasury;
        this.treasuryWithdrawalDestination = treasuryWithdrawalDestination;
        this.feeWithdrawalDestination = feeWithdrawalDestination;
        this.treasuryMint = treasuryMint;
        this.authority = authority;
        this.creator = creator;
        this.bump = bump;
        this.treasuryBump = treasuryBump;
        this.feePayerBump = feePayerBump;
        this.sellerFeeBasisPoints = sellerFeeBasisPoints;
        this.requiresSignOff = requiresSignOff;
        this.canChangeSalePrice = canChangeSalePrice;
        this.escrowPaymentBump = escrowPaymentBump;
        this.hasAuctioneer = hasAuctioneer;
        this.auctioneerAddress = auctioneerAddress;
        this.scopes = scopes;
    }
    static fromArgs(args) {
        return new AuctionHouse(args.auctionHouseFeeAccount, args.auctionHouseTreasury, args.treasuryWithdrawalDestination, args.feeWithdrawalDestination, args.treasuryMint, args.authority, args.creator, args.bump, args.treasuryBump, args.feePayerBump, args.sellerFeeBasisPoints, args.requiresSignOff, args.canChangeSalePrice, args.escrowPaymentBump, args.hasAuctioneer, args.auctioneerAddress, args.scopes);
    }
    static fromAccountInfo(accountInfo, offset = 0) {
        return AuctionHouse.deserialize(accountInfo.data, offset);
    }
    static async fromAccountAddress(connection, address) {
        const accountInfo = await connection.getAccountInfo(address);
        if (accountInfo == null) {
            throw new Error(`Unable to find AuctionHouse account at ${address}`);
        }
        return AuctionHouse.fromAccountInfo(accountInfo, 0)[0];
    }
    static gpaBuilder(programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
        return beetSolana.GpaBuilder.fromStruct(programId, exports.auctionHouseBeet);
    }
    static deserialize(buf, offset = 0) {
        return exports.auctionHouseBeet.deserialize(buf, offset);
    }
    serialize() {
        return exports.auctionHouseBeet.serialize({
            accountDiscriminator: exports.auctionHouseDiscriminator,
            ...this,
        });
    }
    static get byteSize() {
        return exports.auctionHouseBeet.byteSize;
    }
    static async getMinimumBalanceForRentExemption(connection, commitment) {
        return connection.getMinimumBalanceForRentExemption(AuctionHouse.byteSize, commitment);
    }
    static hasCorrectByteSize(buf, offset = 0) {
        return buf.byteLength - offset === AuctionHouse.byteSize;
    }
    pretty() {
        return {
            auctionHouseFeeAccount: this.auctionHouseFeeAccount.toBase58(),
            auctionHouseTreasury: this.auctionHouseTreasury.toBase58(),
            treasuryWithdrawalDestination: this.treasuryWithdrawalDestination.toBase58(),
            feeWithdrawalDestination: this.feeWithdrawalDestination.toBase58(),
            treasuryMint: this.treasuryMint.toBase58(),
            authority: this.authority.toBase58(),
            creator: this.creator.toBase58(),
            bump: this.bump,
            treasuryBump: this.treasuryBump,
            feePayerBump: this.feePayerBump,
            sellerFeeBasisPoints: this.sellerFeeBasisPoints,
            requiresSignOff: this.requiresSignOff,
            canChangeSalePrice: this.canChangeSalePrice,
            escrowPaymentBump: this.escrowPaymentBump,
            hasAuctioneer: this.hasAuctioneer,
            auctioneerAddress: this.auctioneerAddress.toBase58(),
            scopes: this.scopes,
        };
    }
}
exports.AuctionHouse = AuctionHouse;
exports.auctionHouseBeet = new beet.BeetStruct([
    ['accountDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['auctionHouseFeeAccount', beetSolana.publicKey],
    ['auctionHouseTreasury', beetSolana.publicKey],
    ['treasuryWithdrawalDestination', beetSolana.publicKey],
    ['feeWithdrawalDestination', beetSolana.publicKey],
    ['treasuryMint', beetSolana.publicKey],
    ['authority', beetSolana.publicKey],
    ['creator', beetSolana.publicKey],
    ['bump', beet.u8],
    ['treasuryBump', beet.u8],
    ['feePayerBump', beet.u8],
    ['sellerFeeBasisPoints', beet.u16],
    ['requiresSignOff', beet.bool],
    ['canChangeSalePrice', beet.bool],
    ['escrowPaymentBump', beet.u8],
    ['hasAuctioneer', beet.bool],
    ['auctioneerAddress', beetSolana.publicKey],
    ['scopes', beet.uniformFixedSizeArray(beet.bool, 7)],
], AuctionHouse.fromArgs, 'AuctionHouse');
//# sourceMappingURL=AuctionHouse.js.map