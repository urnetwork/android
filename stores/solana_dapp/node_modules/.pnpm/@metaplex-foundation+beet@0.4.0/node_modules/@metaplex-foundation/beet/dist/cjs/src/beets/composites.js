"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.compositesTypeMap = exports.coption = exports.coptionSome = exports.coptionNone = exports.isNoneBuffer = exports.isSomeBuffer = void 0;
const assert_1 = require("assert");
const types_1 = require("../types");
const types_2 = require("../types");
const utils_1 = require("../utils");
const beet_fixable_1 = require("../beet.fixable");
const NONE = 0;
const SOME = 1;
/**
 * @private
 */
function isSomeBuffer(buf, offset) {
    return buf[offset] === SOME;
}
exports.isSomeBuffer = isSomeBuffer;
/**
 * @private
 */
function isNoneBuffer(buf, offset) {
    return buf[offset] === NONE;
}
exports.isNoneBuffer = isNoneBuffer;
/**
 * De/Serializes `None` case of an _Option_ of type {@link T} represented by
 * {@link COption}.
 *
 * The de/serialized type is prefixed with `0`.
 * This matches the `COption::None` type borsh representation.
 *
 * @template T inner option type
 * @param inner the De/Serializer for the inner type
 *
 * @category beet/option
 */
function coptionNone(description) {
    (0, utils_1.logTrace)(`coptionNone(${description})`);
    return {
        write: function (buf, offset, value) {
            (0, assert_1.strict)(value == null, 'coptionNone can only handle `null` values');
            buf[offset] = NONE;
        },
        read: function (buf, offset) {
            (0, assert_1.strict)(isNoneBuffer(buf, offset), 'coptionNone can only handle `NONE` data');
            return null;
        },
        byteSize: 1,
        description: `COption<None(${description})>`,
    };
}
exports.coptionNone = coptionNone;
/**
 * De/Serializes `Some` case of an _Option_ of type {@link T} represented by
 * {@link COption}.
 *
 * The de/serialized type is prefixed with `1`.
 * This matches the `COption::Some` type borsh representation.
 *
 * @template T inner option type
 * @param inner the De/Serializer for the inner type
 *
 * @category beet/composite
 */
function coptionSome(inner) {
    const byteSize = 1 + inner.byteSize;
    const beet = {
        write: function (buf, offset, value) {
            (0, types_1.assertFixedSizeBeet)(inner, `coption inner type ${inner.description} needs to be fixed before calling write`);
            (0, assert_1.strict)(value != null, 'coptionSome cannot handle `null` values');
            buf[offset] = SOME;
            inner.write(buf, offset + 1, value);
        },
        read: function (buf, offset) {
            (0, types_1.assertFixedSizeBeet)(inner, `coption inner type ${inner.description} needs to be fixed before calling read`);
            (0, assert_1.strict)(isSomeBuffer(buf, offset), 'coptionSome can only handle `SOME` data');
            return inner.read(buf, offset + 1);
        },
        description: `COption<${inner.description}>[1 + ${inner.byteSize}]`,
        byteSize,
        inner,
    };
    (0, utils_1.logTrace)(beet.description);
    return beet;
}
exports.coptionSome = coptionSome;
/**
 * De/Serializes an _Option_ of type {@link T} represented by {@link COption}.
 *
 * The de/serialized type is prefixed with `1` if the inner value is present
 * and with `0` if not.
 * This matches the `COption` type borsh representation.
 *
 * @template T inner option type
 * @param inner the De/Serializer for the inner type
 *
 * @category beet/composite
 */
function coption(inner) {
    return {
        toFixedFromData(buf, offset) {
            if (isSomeBuffer(buf, offset)) {
                const innerFixed = (0, beet_fixable_1.fixBeetFromData)(inner, buf, offset + 1);
                return coptionSome(innerFixed);
            }
            else {
                (0, assert_1.strict)(isNoneBuffer(buf, offset), `Expected ${buf} to hold a COption`);
                return coptionNone(inner.description);
            }
        },
        toFixedFromValue(val) {
            return val == null
                ? coptionNone(inner.description)
                : coptionSome((0, beet_fixable_1.fixBeetFromValue)(inner, val));
        },
        description: `COption<${inner.description}>`,
    };
}
exports.coption = coption;
/**
 * Maps composite beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
exports.compositesTypeMap = {
    option: {
        beet: 'coption',
        isFixable: true,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'COption<Inner>',
        arg: types_1.BEET_TYPE_ARG_INNER,
        pack: types_2.BEET_PACKAGE,
    },
};
//# sourceMappingURL=composites.js.map