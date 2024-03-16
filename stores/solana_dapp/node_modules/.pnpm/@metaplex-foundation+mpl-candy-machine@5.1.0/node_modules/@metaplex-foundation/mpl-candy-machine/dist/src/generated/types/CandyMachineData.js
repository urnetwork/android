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
exports.candyMachineDataBeet = void 0;
const beet = __importStar(require("@metaplex-foundation/beet"));
const EndSettings_1 = require("./EndSettings");
const Creator_1 = require("./Creator");
const HiddenSettings_1 = require("./HiddenSettings");
const WhitelistMintSettings_1 = require("./WhitelistMintSettings");
const GatekeeperConfig_1 = require("./GatekeeperConfig");
exports.candyMachineDataBeet = new beet.FixableBeetArgsStruct([
    ['uuid', beet.utf8String],
    ['price', beet.u64],
    ['symbol', beet.utf8String],
    ['sellerFeeBasisPoints', beet.u16],
    ['maxSupply', beet.u64],
    ['isMutable', beet.bool],
    ['retainAuthority', beet.bool],
    ['goLiveDate', beet.coption(beet.i64)],
    ['endSettings', beet.coption(EndSettings_1.endSettingsBeet)],
    ['creators', beet.array(Creator_1.creatorBeet)],
    ['hiddenSettings', beet.coption(HiddenSettings_1.hiddenSettingsBeet)],
    ['whitelistMintSettings', beet.coption(WhitelistMintSettings_1.whitelistMintSettingsBeet)],
    ['itemsAvailable', beet.u64],
    ['gatekeeper', beet.coption(GatekeeperConfig_1.gatekeeperConfigBeet)],
], 'CandyMachineData');
//# sourceMappingURL=CandyMachineData.js.map