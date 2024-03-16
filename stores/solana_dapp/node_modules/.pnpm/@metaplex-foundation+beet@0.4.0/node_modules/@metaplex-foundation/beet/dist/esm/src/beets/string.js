import { BEET_PACKAGE, BEET_TYPE_ARG_LEN, } from '../types';
import { strict as assert } from 'assert';
import { u32 } from './numbers';
import { logTrace } from '../utils';
/**
 * De/Serializes a UTF8 string of a particular size.
 *
 * @param stringByteLength the number of bytes of the string
 *
 * @category beet/collection
 */
export const fixedSizeUtf8String = (stringByteLength) => {
    return {
        write: function (buf, offset, value) {
            const stringBuf = Buffer.from(value, 'utf8');
            assert.equal(stringBuf.byteLength, stringByteLength, `${value} has invalid byte size`);
            u32.write(buf, offset, stringByteLength);
            stringBuf.copy(buf, offset + 4, 0, stringByteLength);
        },
        read: function (buf, offset) {
            const size = u32.read(buf, offset);
            assert.equal(size, stringByteLength, `invalid byte size`);
            const stringSlice = buf.slice(offset + 4, offset + 4 + stringByteLength);
            return stringSlice.toString('utf8');
        },
        elementByteSize: 1,
        length: stringByteLength,
        lenPrefixByteSize: 4,
        byteSize: 4 + stringByteLength,
        description: `Utf8String(4 + ${stringByteLength})`,
    };
};
/**
 * De/Serializes a UTF8 string of any size.
 *
 * @category beet/collection
 */
export const utf8String = {
    toFixedFromData(buf, offset) {
        const len = u32.read(buf, offset);
        logTrace(`${this.description}[${len}]`);
        return fixedSizeUtf8String(len);
    },
    toFixedFromValue(val) {
        const len = Buffer.from(val).byteLength;
        return fixedSizeUtf8String(len);
    },
    description: `Utf8String`,
};
/**
 * Maps string beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export const stringTypeMap = {
    fixedSizeString: {
        beet: 'fixedSizeUtf8String',
        isFixable: false,
        sourcePack: BEET_PACKAGE,
        ts: 'string',
        arg: BEET_TYPE_ARG_LEN,
    },
    string: {
        beet: 'utf8String',
        isFixable: true,
        sourcePack: BEET_PACKAGE,
        ts: 'string',
    },
};
//# sourceMappingURL=string.js.map