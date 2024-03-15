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
exports.auctioneerBeet = exports.Auctioneer = exports.auctioneerDiscriminator = void 0;
const web3 = __importStar(require("@solana/web3.js"));
const beetSolana = __importStar(require("@metaplex-foundation/beet-solana"));
const beet = __importStar(require("@metaplex-foundation/beet"));
exports.auctioneerDiscriminator = [46, 101, 92, 150, 138, 30, 245, 120];
class Auctioneer {
    constructor(auctioneerAuthority, auctionHouse, bump) {
        this.auctioneerAuthority = auctioneerAuthority;
        this.auctionHouse = auctionHouse;
        this.bump = bump;
    }
    static fromArgs(args) {
        return new Auctioneer(args.auctioneerAuthority, args.auctionHouse, args.bump);
    }
    static fromAccountInfo(accountInfo, offset = 0) {
        return Auctioneer.deserialize(accountInfo.data, offset);
    }
    static async fromAccountAddress(connection, address) {
        const accountInfo = await connection.getAccountInfo(address);
        if (accountInfo == null) {
            throw new Error(`Unable to find Auctioneer account at ${address}`);
        }
        return Auctioneer.fromAccountInfo(accountInfo, 0)[0];
    }
    static gpaBuilder(programId = new web3.PublicKey('hausS13jsjafwWwGqZTUQRmWyvyxn9EQpqMwV1PBBmk')) {
        return beetSolana.GpaBuilder.fromStruct(programId, exports.auctioneerBeet);
    }
    static deserialize(buf, offset = 0) {
        return exports.auctioneerBeet.deserialize(buf, offset);
    }
    serialize() {
        return exports.auctioneerBeet.serialize({
            accountDiscriminator: exports.auctioneerDiscriminator,
            ...this,
        });
    }
    static get byteSize() {
        return exports.auctioneerBeet.byteSize;
    }
    static async getMinimumBalanceForRentExemption(connection, commitment) {
        return connection.getMinimumBalanceForRentExemption(Auctioneer.byteSize, commitment);
    }
    static hasCorrectByteSize(buf, offset = 0) {
        return buf.byteLength - offset === Auctioneer.byteSize;
    }
    pretty() {
        return {
            auctioneerAuthority: this.auctioneerAuthority.toBase58(),
            auctionHouse: this.auctionHouse.toBase58(),
            bump: this.bump,
        };
    }
}
exports.Auctioneer = Auctioneer;
exports.auctioneerBeet = new beet.BeetStruct([
    ['accountDiscriminator', beet.uniformFixedSizeArray(beet.u8, 8)],
    ['auctioneerAuthority', beetSolana.publicKey],
    ['auctionHouse', beetSolana.publicKey],
    ['bump', beet.u8],
], Auctioneer.fromArgs, 'Auctioneer');
//# sourceMappingURL=Auctioneer.js.map