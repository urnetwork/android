"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.fixBeetFromValue = exports.fixBeetFromData = void 0;
const types_1 = require("./types");
/**
 * Converts the provided beet into a {@link FixedBeet} unless it already is.
 * The sizes for {@link FixableBeet}s are determined from the provided data.
 *
 * @param beet to convert
 * @param buf containing serialized data that the fixed beet needs to process
 * @param offset at which the data for the beet starts
 *
 * @category beet
 */
function fixBeetFromData(beet, buf, offset) {
    if ((0, types_1.isFixedSizeBeet)(beet)) {
        return beet;
    }
    if ((0, types_1.isFixableBeet)(beet)) {
        return beet.toFixedFromData(buf, offset);
    }
    throw new Error(`${beet.description} is neither fixed size nor fixable`);
}
exports.fixBeetFromData = fixBeetFromData;
/**
 * Converts the provided beet into a {@link FixedBeet} unless it already is.
 * The sizes for {@link FixableBeet}s are determined from the provided value.
 *
 * @param beet to convert
 * @param val value that the fixed beet needs to process
 *
 * @category beet
 */
function fixBeetFromValue(beet, val) {
    if ((0, types_1.isFixedSizeBeet)(beet)) {
        return beet;
    }
    if ((0, types_1.isFixableBeet)(beet)) {
        return beet.toFixedFromValue(val);
    }
    throw new Error(`${beet.description} is neither fixed size nor fixable`);
}
exports.fixBeetFromValue = fixBeetFromValue;
//# sourceMappingURL=beet.fixable.js.map