"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const web3_js_1 = require("@solana/web3.js");
const generated_1 = require("../src/generated");
const tape_1 = __importDefault(require("tape"));
const spok_1 = __importDefault(require("spok"));
function quickKeypair() {
    const kp = web3_js_1.Keypair.generate();
    return [kp.publicKey, kp.secretKey];
}
(0, tape_1.default)('account auction-house: round trip serilization', async (t) => {
    const [creator] = quickKeypair();
    const [auctionHouseTreasury] = quickKeypair();
    const [treasuryWithdrawalDestination] = quickKeypair();
    const [feeWithdrawalDestination] = quickKeypair();
    const [treasuryMint] = quickKeypair();
    const args = {
        auctionHouseFeeAccount: creator,
        auctionHouseTreasury,
        treasuryWithdrawalDestination,
        feeWithdrawalDestination,
        treasuryMint,
        authority: creator,
        creator,
        bump: 0,
        treasuryBump: 1,
        feePayerBump: 2,
        sellerFeeBasisPoints: 3,
        requiresSignOff: false,
        canChangeSalePrice: true,
        escrowPaymentBump: 255,
        hasAuctioneer: false,
        auctioneerAddress: web3_js_1.PublicKey.default,
        scopes: [true, false, true, false, true, true, false],
    };
    const expected = generated_1.AuctionHouse.fromArgs(args);
    const [data] = expected.serialize();
    const info = {
        executable: false,
        data,
        owner: creator,
        lamports: 1000,
    };
    const actual = generated_1.AuctionHouse.fromAccountInfo(info)[0];
    (0, spok_1.default)(t, actual, expected);
});
//# sourceMappingURL=account.auction-house.test.js.map