"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.collectionsTypeMap = exports.uint8Array = exports.fixedSizeUint8Array = exports.fixedSizeBuffer = exports.array = exports.fixedSizeArray = exports.uniformFixedSizeArray = void 0;
const types_1 = require("../types");
const assert_1 = require("assert");
const numbers_1 = require("./numbers");
const types_2 = require("../types");
const utils_1 = require("../utils");
const beet_fixable_1 = require("../beet.fixable");
/**
 * De/Serializes an array with a specific number of elements of type {@link T}
 * which all have the same size.
 *
 * @template T type of elements held in the array
 *
 * @param element the De/Serializer for the element type
 * @param len the number of elements in the array
 * @param lenPrefix if `true` a 4 byte number indicating the size of the array
 * will be included before serialized array data
 *
 * @category beet/collection
 */
function uniformFixedSizeArray(element, len, lenPrefix = false) {
    const arraySize = element.byteSize * len;
    const byteSize = lenPrefix ? 4 + arraySize : arraySize;
    return {
        write: function (buf, offset, value) {
            assert_1.strict.equal(value.length, len, `array length ${value.length} should match len ${len}`);
            if (lenPrefix) {
                numbers_1.u32.write(buf, offset, len);
                offset += 4;
            }
            for (let i = 0; i < len; i++) {
                element.write(buf, offset + i * element.byteSize, value[i]);
            }
        },
        read: function (buf, offset) {
            if (lenPrefix) {
                const size = numbers_1.u32.read(buf, offset);
                assert_1.strict.equal(size, len, 'invalid byte size');
                offset += 4;
            }
            const arr = new Array(len);
            for (let i = 0; i < len; i++) {
                arr[i] = element.read(buf, offset + i * element.byteSize);
            }
            return arr;
        },
        byteSize,
        length: len,
        elementByteSize: element.byteSize,
        lenPrefixByteSize: 4,
        description: `Array<${element.description}>(${len})`,
    };
}
exports.uniformFixedSizeArray = uniformFixedSizeArray;
/**
 * De/Serializes an array with a specific number of elements of type {@link T}
 * which do not all have the same size.
 *
 * @template T type of elements held in the array
 *
 * @param elements the De/Serializers for the element types
 * @param elementsByteSize size of all elements in the array combined
 *
 * @category beet/collection
 */
function fixedSizeArray(elements, elementsByteSize) {
    const len = elements.length;
    const firstElement = len === 0 ? '<EMPTY>' : elements[0].description;
    return {
        write: function (buf, offset, value) {
            assert_1.strict.equal(value.length, len, `array length ${value.length} should match len ${len}`);
            numbers_1.u32.write(buf, offset, len);
            let cursor = offset + 4;
            for (let i = 0; i < len; i++) {
                const element = elements[i];
                element.write(buf, cursor, value[i]);
                cursor += element.byteSize;
            }
        },
        read: function (buf, offset) {
            const size = numbers_1.u32.read(buf, offset);
            assert_1.strict.equal(size, len, 'invalid byte size');
            let cursor = offset + 4;
            const arr = new Array(len);
            for (let i = 0; i < len; i++) {
                const element = elements[i];
                arr[i] = element.read(buf, cursor);
                cursor += element.byteSize;
            }
            return arr;
        },
        byteSize: 4 + elementsByteSize,
        length: len,
        description: `Array<${firstElement}>(${len})[ 4 + ${elementsByteSize} ]`,
    };
}
exports.fixedSizeArray = fixedSizeArray;
/**
 * Wraps an array De/Serializer with with elements of type {@link T} which do
 * not all have the same size.
 *
 * @template T type of elements held in the array
 *
 * @param element the De/Serializer for the element types
 *
 * @category beet/collection
 */
function array(element) {
    return {
        toFixedFromData(buf, offset) {
            const len = numbers_1.u32.read(buf, offset);
            (0, utils_1.logTrace)(`${this.description}[${len}]`);
            const cursorStart = offset + 4;
            let cursor = cursorStart;
            const fixedElements = new Array(len);
            for (let i = 0; i < len; i++) {
                const fixedElement = (0, beet_fixable_1.fixBeetFromData)(element, buf, cursor);
                fixedElements[i] = fixedElement;
                cursor += fixedElement.byteSize;
            }
            return fixedSizeArray(fixedElements, cursor - cursorStart);
        },
        toFixedFromValue(vals) {
            (0, assert_1.strict)(Array.isArray(vals), `${vals} should be an array`);
            let elementsSize = 0;
            const fixedElements = new Array(vals.length);
            for (let i = 0; i < vals.length; i++) {
                const fixedElement = (0, beet_fixable_1.fixBeetFromValue)(element, vals[i]);
                fixedElements[i] = fixedElement;
                elementsSize += fixedElement.byteSize;
            }
            return fixedSizeArray(fixedElements, elementsSize);
        },
        description: `array`,
    };
}
exports.array = array;
/**
 * A De/Serializer for raw {@link Buffer}s that just copies/reads the buffer bytes
 * to/from the provided buffer.
 *
 * @param bytes the byte size of the buffer to de/serialize
 * @category beet/collection
 */
function fixedSizeBuffer(bytes) {
    return {
        write: function (buf, offset, value) {
            value.copy(buf, offset, 0, bytes);
        },
        read: function (buf, offset) {
            return buf.slice(offset, offset + bytes);
        },
        byteSize: bytes,
        description: `Buffer(${bytes})`,
    };
}
exports.fixedSizeBuffer = fixedSizeBuffer;
/**
 * A De/Serializer for {@link Uint8Array}s of known size that just copies/reads
 * the array bytes to/from the provided buffer.
 *
 * @category beet/collection
 */
function fixedSizeUint8Array(len, lenPrefix = false) {
    const arrayBufferBeet = fixedSizeBuffer(len);
    const byteSize = lenPrefix ? len + 4 : len;
    return {
        write: function (buf, offset, value) {
            assert_1.strict.equal(value.byteLength, len, `Uint8Array length ${value.byteLength} should match len ${len}`);
            if (lenPrefix) {
                numbers_1.u32.write(buf, offset, len);
                offset += 4;
            }
            const valueBuf = Buffer.from(value);
            arrayBufferBeet.write(buf, offset, valueBuf);
        },
        read: function (buf, offset) {
            if (lenPrefix) {
                const size = numbers_1.u32.read(buf, offset);
                assert_1.strict.equal(size, len, 'invalid byte size');
                offset += 4;
            }
            const arrayBuffer = arrayBufferBeet.read(buf, offset);
            return Uint8Array.from(arrayBuffer);
        },
        byteSize,
        description: `Uint8Array(${len})`,
    };
}
exports.fixedSizeUint8Array = fixedSizeUint8Array;
/**
 * A De/Serializer for {@link Uint8Array}s that just copies/reads the array bytes
 * to/from the provided buffer.
 *
 * @category beet/collection
 */
exports.uint8Array = {
    toFixedFromData(buf, offset) {
        const len = numbers_1.u32.read(buf, offset);
        (0, utils_1.logTrace)(`${this.description}[${len}]`);
        return fixedSizeUint8Array(len, true);
    },
    toFixedFromValue(val) {
        const len = val.byteLength;
        return fixedSizeUint8Array(len, true);
    },
    description: `Uint8Array`,
};
/**
 * Maps collections beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
exports.collectionsTypeMap = {
    Array: {
        beet: 'array',
        isFixable: true,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Array',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
    FixedSizeArray: {
        beet: 'fixedSizeArray',
        isFixable: false,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Array',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
    UniformFixedSizeArray: {
        beet: 'uniformFixedSizeArray',
        isFixable: false,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Array',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
    Buffer: {
        beet: 'fixedSizeBuffer',
        isFixable: false,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Buffer',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
    FixedSizeUint8Array: {
        beet: 'fixedSizeUint8Array',
        isFixable: false,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Uint8Array',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
    Uint8Array: {
        beet: 'uint8Array',
        isFixable: true,
        sourcePack: types_2.BEET_PACKAGE,
        ts: 'Uint8Array',
        arg: types_1.BEET_TYPE_ARG_LEN,
    },
};
//# sourceMappingURL=collections.js.map