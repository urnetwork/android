import { strict as assert } from 'assert';
/**
 * Matches name in package.json
 *
 * @private
 */
export const BEET_PACKAGE = '@metaplex-foundation/beet';
/**
 * @private
 * @category beet
 */
export const BEET_TYPE_ARG_LEN = 'len';
/**
 * @private
 * @category beet
 */
export const BEET_TYPE_ARG_INNER = 'Beet<{innner}>';
// -----------------
// Guards
// -----------------
/**
 * @private
 */
export function isFixedSizeBeet(x) {
    return Object.keys(x).includes('byteSize');
}
/**
 * @private
 */
export function assertFixedSizeBeet(x, msg = `${x} should have been a fixed beet`) {
    assert(isFixedSizeBeet(x), msg);
}
/**
 * @private
 */
export function isFixableBeet(x) {
    return (typeof x.toFixedFromData === 'function' &&
        typeof x.toFixedFromValue === 'function');
}
/**
 * @private
 */
export function isElementCollectionFixedSizeBeet(x) {
    const keys = Object.keys(x);
    return (keys.includes('length') &&
        keys.includes('elementByteSize') &&
        keys.includes('lenPrefixByteSize'));
}
//# sourceMappingURL=types.js.map