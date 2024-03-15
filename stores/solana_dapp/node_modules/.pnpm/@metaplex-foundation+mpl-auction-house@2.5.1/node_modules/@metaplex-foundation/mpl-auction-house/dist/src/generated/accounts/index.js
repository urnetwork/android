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
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.accountProviders = void 0;
__exportStar(require("./AuctionHouse"), exports);
__exportStar(require("./Auctioneer"), exports);
__exportStar(require("./BidReceipt"), exports);
__exportStar(require("./ListingReceipt"), exports);
__exportStar(require("./PurchaseReceipt"), exports);
const BidReceipt_1 = require("./BidReceipt");
const ListingReceipt_1 = require("./ListingReceipt");
const PurchaseReceipt_1 = require("./PurchaseReceipt");
const AuctionHouse_1 = require("./AuctionHouse");
const Auctioneer_1 = require("./Auctioneer");
exports.accountProviders = {
    BidReceipt: BidReceipt_1.BidReceipt,
    ListingReceipt: ListingReceipt_1.ListingReceipt,
    PurchaseReceipt: PurchaseReceipt_1.PurchaseReceipt,
    AuctionHouse: AuctionHouse_1.AuctionHouse,
    Auctioneer: Auctioneer_1.Auctioneer,
};
//# sourceMappingURL=index.js.map