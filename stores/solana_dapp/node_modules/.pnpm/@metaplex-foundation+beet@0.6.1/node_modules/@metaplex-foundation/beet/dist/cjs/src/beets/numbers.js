"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.numbersTypeMap = exports.bool = exports.i512 = exports.i256 = exports.i128 = exports.i64 = exports.i32 = exports.i16 = exports.i8 = exports.u512 = exports.u256 = exports.u128 = exports.u64 = exports.u32 = exports.u16 = exports.u8 = void 0;
const bn_js_1 = __importDefault(require("bn.js"));
const types_1 = require("../types");
// -----------------
// Unsigned
// -----------------
/**
 * De/Serializer for 8-bit unsigned integers aka `u8`.
 *
 * @category beet/primitive
 */
exports.u8 = {
    write: function (buf, offset, value) {
        buf.writeUInt8(value, offset);
    },
    read: function (buf, offset) {
        return buf.readUInt8(offset);
    },
    byteSize: 1,
    description: 'u8',
};
/**
 * De/Serializer 16-bit unsigned integers aka `u16`.
 *
 * @category beet/primitive
 */
exports.u16 = {
    write: function (buf, offset, value) {
        buf.writeUInt16LE(value, offset);
    },
    read: function (buf, offset) {
        return buf.readUInt16LE(offset);
    },
    byteSize: 2,
    description: 'u16',
};
/**
 * De/Serializer for 32-bit unsigned integers aka `u32`.
 *
 * @category beet/primitive
 */
exports.u32 = {
    write: function (buf, offset, value) {
        buf.writeUInt32LE(value, offset);
    },
    read: function (buf, offset) {
        return buf.readUInt32LE(offset);
    },
    byteSize: 4,
    description: 'u32',
};
function unsignedLargeBeet(byteSize, description) {
    return {
        write: function (buf, offset, value) {
            const bn = bn_js_1.default.isBN(value) ? value : new bn_js_1.default(value);
            const bytesArray = bn.toArray('le', this.byteSize);
            const bytesArrayBuf = Buffer.from(bytesArray);
            bytesArrayBuf.copy(buf, offset, 0, this.byteSize);
        },
        read: function (buf, offset) {
            const slice = buf.slice(offset, offset + this.byteSize);
            return new bn_js_1.default(slice, 'le');
        },
        byteSize,
        description,
    };
}
/**
 * De/Serializer for 64-bit unsigned integers aka `u64` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.u64 = unsignedLargeBeet(8, 'u64');
/**
 * De/Serializer for 128-bit unsigned integers aka `u128` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.u128 = unsignedLargeBeet(16, 'u128');
/**
 * De/Serializer for 256-bit unsigned integers aka `u256` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.u256 = unsignedLargeBeet(32, 'u256');
/**
 * De/Serializer for 512-bit unsigned integers aka `u512` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.u512 = unsignedLargeBeet(64, 'u512');
// -----------------
// Signed
// -----------------
/**
 * De/Serializer 8-bit signed integers aka `i8`.
 *
 * @category beet/primitive
 */
exports.i8 = {
    write: function (buf, offset, value) {
        buf.writeInt8(value, offset);
    },
    read: function (buf, offset) {
        return buf.readInt8(offset);
    },
    byteSize: 1,
    description: 'i8',
};
/**
 * De/Serializer 16-bit signed integers aka `i16`.
 *
 * @category beet/primitive
 */
exports.i16 = {
    write: function (buf, offset, value) {
        buf.writeInt16LE(value, offset);
    },
    read: function (buf, offset) {
        return buf.readInt16LE(offset);
    },
    byteSize: 2,
    description: 'i16',
};
/**
 * De/Serializer 32-bit signed integers aka `i32`.
 *
 * @category beet/primitive
 */
exports.i32 = {
    write: function (buf, offset, value) {
        buf.writeInt32LE(value, offset);
    },
    read: function (buf, offset) {
        return buf.readInt32LE(offset);
    },
    byteSize: 4,
    description: 'i32',
};
function signedLargeBeet(byteSize, description) {
    const bitSize = byteSize * 8;
    return {
        write: function (buf, offset, value) {
            const bn = (bn_js_1.default.isBN(value) ? value : new bn_js_1.default(value)).toTwos(bitSize);
            const bytesArray = bn.toArray('le', this.byteSize);
            const bytesArrayBuf = Buffer.from(bytesArray);
            bytesArrayBuf.copy(buf, offset, 0, this.byteSize);
        },
        read: function (buf, offset) {
            const slice = buf.slice(offset, offset + this.byteSize);
            const x = new bn_js_1.default(slice, 'le');
            return x.fromTwos(bitSize);
        },
        byteSize,
        description,
    };
}
/**
 * De/Serializer for 64-bit signed integers aka `i64` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.i64 = signedLargeBeet(8, 'i64');
/**
 * De/Serializer for 128-bit signed integers aka `i128` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.i128 = signedLargeBeet(16, 'i128');
/**
 * De/Serializer for 256-bit signed integers aka `i256` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.i256 = signedLargeBeet(32, 'i256');
/**
 * De/Serializer for 512-bit signed integers aka `i512` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
exports.i512 = signedLargeBeet(64, 'i512');
// -----------------
// Boolean
// -----------------
/**
 * De/Serializer booleans aka `bool`.
 *
 * @category beet/primitive
 */
exports.bool = {
    write: function (buf, offset, value) {
        const n = value ? 1 : 0;
        exports.u8.write(buf, offset, n);
    },
    read: function (buf, offset) {
        return exports.u8.read(buf, offset) === 1;
    },
    byteSize: 1,
    description: 'bool',
};
/**
 * Maps primitive beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
// prettier-ignore
exports.numbersTypeMap = {
    // <= 32-bit numbers and boolean
    u8: { beet: 'u8', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    u16: { beet: 'u16', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    u32: { beet: 'u32', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    i8: { beet: 'i8', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    i16: { beet: 'i16', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    i32: { beet: 'i32', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'number' },
    bool: { beet: 'bool', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'boolean' },
    // Big Number, they use, the 'bignum' type which is defined in this package
    u64: { beet: 'u64', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    u128: { beet: 'u128', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    u256: { beet: 'u256', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    u512: { beet: 'u512', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    i64: { beet: 'i64', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    i128: { beet: 'i128', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    i256: { beet: 'i256', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
    i512: { beet: 'i512', isFixable: false, sourcePack: types_1.BEET_PACKAGE, ts: 'bignum', pack: types_1.BEET_PACKAGE },
};
//# sourceMappingURL=numbers.js.map