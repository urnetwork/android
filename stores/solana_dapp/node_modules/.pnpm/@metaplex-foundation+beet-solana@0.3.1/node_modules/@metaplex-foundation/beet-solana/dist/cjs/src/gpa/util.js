"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.encodeFixedBeet = void 0;
const bs58_1 = __importDefault(require("bs58"));
function encodeFixedBeet(beet, val) {
    const buf = Buffer.alloc(beet.byteSize);
    beet.write(buf, 0, val);
    return bs58_1.default.encode(buf);
}
exports.encodeFixedBeet = encodeFixedBeet;
//# sourceMappingURL=util.js.map