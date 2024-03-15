import { BEET_TYPE_ARG_LEN, } from '../types';
import { strict as assert } from 'assert';
import { u32 } from './numbers';
import { BEET_PACKAGE } from '../types';
import { logTrace } from '../utils';
import { fixBeetFromData, fixBeetFromValue } from '../beet.fixable';
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
export function uniformFixedSizeArray(element, len, lenPrefix = false) {
    const arraySize = element.byteSize * len;
    const byteSize = lenPrefix ? 4 + arraySize : arraySize;
    return {
        write: function (buf, offset, value) {
            assert.equal(value.length, len, `array length ${value.length} should match len ${len}`);
            if (lenPrefix) {
                u32.write(buf, offset, len);
                offset += 4;
            }
            for (let i = 0; i < len; i++) {
                element.write(buf, offset + i * element.byteSize, value[i]);
            }
        },
        read: function (buf, offset) {
            if (lenPrefix) {
                const size = u32.read(buf, offset);
                assert.equal(size, len, 'invalid byte size');
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
export function fixedSizeArray(elements, elementsByteSize) {
    const len = elements.length;
    const firstElement = len === 0 ? '<EMPTY>' : elements[0].description;
    return {
        write: function (buf, offset, value) {
            assert.equal(value.length, len, `array length ${value.length} should match len ${len}`);
            u32.write(buf, offset, len);
            let cursor = offset + 4;
            for (let i = 0; i < len; i++) {
                const element = elements[i];
                element.write(buf, cursor, value[i]);
                cursor += element.byteSize;
            }
        },
        read: function (buf, offset) {
            const size = u32.read(buf, offset);
            assert.equal(size, len, 'invalid byte size');
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
export function array(element) {
    return {
        toFixedFromData(buf, offset) {
            const len = u32.read(buf, offset);
            logTrace(`${this.description}[${len}]`);
            const cursorStart = offset + 4;
            let cursor = cursorStart;
            const fixedElements = new Array(len);
            for (let i = 0; i < len; i++) {
                const fixedElement = fixBeetFromData(element, buf, cursor);
                fixedElements[i] = fixedElement;
                cursor += fixedElement.byteSize;
            }
            return fixedSizeArray(fixedElements, cursor - cursorStart);
        },
        toFixedFromValue(vals) {
            assert(Array.isArray(vals), `${vals} should be an array`);
            let elementsSize = 0;
            const fixedElements = new Array(vals.length);
            for (let i = 0; i < vals.length; i++) {
                const fixedElement = fixBeetFromValue(element, vals[i]);
                fixedElements[i] = fixedElement;
                elementsSize += fixedElement.byteSize;
            }
            return fixedSizeArray(fixedElements, elementsSize);
        },
        description: `array`,
    };
}
/**
 * A De/Serializer for raw {@link Buffer}s that just copies/reads the buffer bytes
 * to/from the provided buffer.
 *
 * @param bytes the byte size of the buffer to de/serialize
 * @category beet/collection
 */
export function fixedSizeBuffer(bytes) {
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
/**
 * A De/Serializer for {@link Uint8Array}s of known size that just copies/reads
 * the array bytes to/from the provided buffer.
 *
 * @category beet/collection
 */
export function fixedSizeUint8Array(len, lenPrefix = false) {
    const arrayBufferBeet = fixedSizeBuffer(len);
    const byteSize = lenPrefix ? len + 4 : len;
    return {
        write: function (buf, offset, value) {
            assert.equal(value.byteLength, len, `Uint8Array length ${value.byteLength} should match len ${len}`);
            if (lenPrefix) {
                u32.write(buf, offset, len);
                offset += 4;
            }
            const valueBuf = Buffer.from(value);
            arrayBufferBeet.write(buf, offset, valueBuf);
        },
        read: function (buf, offset) {
            if (lenPrefix) {
                const size = u32.read(buf, offset);
                assert.equal(size, len, 'invalid byte size');
                offset += 4;
            }
            const arrayBuffer = arrayBufferBeet.read(buf, offset);
            return Uint8Array.from(arrayBuffer);
        },
        byteSize,
        description: `Uint8Array(${len})`,
    };
}
/**
 * A De/Serializer for {@link Uint8Array}s that just copies/reads the array bytes
 * to/from the provided buffer.
 *
 * @category beet/collection
 */
export const uint8Array = {
    toFixedFromData(buf, offset) {
        const len = u32.read(buf, offset);
        logTrace(`${this.description}[${len}]`);
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
export const collectionsTypeMap = {
    Array: {
        beet: 'array',
        isFixable: true,
        sourcePack: BEET_PACKAGE,
        ts: 'Array',
        arg: BEET_TYPE_ARG_LEN,
    },
    FixedSizeArray: {
        beet: 'fixedSizeArray',
        isFixable: false,
        sourcePack: BEET_PACKAGE,
        ts: 'Array',
        arg: BEET_TYPE_ARG_LEN,
    },
    UniformFixedSizeArray: {
        beet: 'uniformFixedSizeArray',
        isFixable: false,
        sourcePack: BEET_PACKAGE,
        ts: 'Array',
        arg: BEET_TYPE_ARG_LEN,
    },
    Buffer: {
        beet: 'fixedSizeBuffer',
        isFixable: false,
        sourcePack: BEET_PACKAGE,
        ts: 'Buffer',
        arg: BEET_TYPE_ARG_LEN,
    },
    FixedSizeUint8Array: {
        beet: 'fixedSizeUint8Array',
        isFixable: false,
        sourcePack: BEET_PACKAGE,
        ts: 'Uint8Array',
        arg: BEET_TYPE_ARG_LEN,
    },
    Uint8Array: {
        beet: 'uint8Array',
        isFixable: true,
        sourcePack: BEET_PACKAGE,
        ts: 'Uint8Array',
        arg: BEET_TYPE_ARG_LEN,
    },
};
//# sourceMappingURL=collections.js.map