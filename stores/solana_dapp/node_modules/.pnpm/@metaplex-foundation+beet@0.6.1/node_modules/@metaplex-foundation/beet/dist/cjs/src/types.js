"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isElementCollectionFixedSizeBeet = exports.isFixableBeet = exports.assertFixedSizeBeet = exports.isFixedSizeBeet = exports.BEET_TYPE_ARG_INNER = exports.BEET_TYPE_ARG_LEN = exports.BEET_PACKAGE = void 0;
const assert_1 = require("assert");
/**
 * Matches name in package.json
 *
 * @private
 */
exports.BEET_PACKAGE = '@metaplex-foundation/beet';
/**
 * @private
 * @category beet
 */
exports.BEET_TYPE_ARG_LEN = 'len';
/**
 * @private
 * @category beet
 */
exports.BEET_TYPE_ARG_INNER = 'Beet<{innner}>';
// -----------------
// Guards
// -----------------
/**
 * @private
 */
function isFixedSizeBeet(x) {
    return Object.keys(x).includes('byteSize');
}
exports.isFixedSizeBeet = isFixedSizeBeet;
/**
 * @private
 */
function assertFixedSizeBeet(x, msg = `${x} should have been a fixed beet`) {
    (0, assert_1.strict)(isFixedSizeBeet(x), msg);
}
exports.assertFixedSizeBeet = assertFixedSizeBeet;
/**
 * @private
 */
function isFixableBeet(x) {
    return (typeof x.toFixedFromData === 'function' &&
        typeof x.toFixedFromValue === 'function');
}
exports.isFixableBeet = isFixableBeet;
/**
 * @private
 */
function isElementCollectionFixedSizeBeet(x) {
    const keys = Object.keys(x);
    return (keys.includes('length') &&
        keys.includes('elementByteSize') &&
        keys.includes('lenPrefixByteSize'));
}
exports.isElementCollectionFixedSizeBeet = isElementCollectionFixedSizeBeet;
//# sourceMappingURL=types.js.map