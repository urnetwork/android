/// <reference types="node" />
import { FixedSizeBeet, SupportedTypeDefinition, ElementCollectionBeet, FixableBeet, Beet } from '../types';
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
export declare function uniformFixedSizeArray<T, V = Partial<T>>(element: FixedSizeBeet<T, V>, len: number, lenPrefix?: boolean): ElementCollectionBeet & FixedSizeBeet<T[], V[]>;
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
export declare function fixedSizeArray<T, V = Partial<T>>(elements: FixedSizeBeet<T, V>[], elementsByteSize: number): FixedSizeBeet<T[], V[]>;
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
export declare function array<T, V = Partial<T>>(element: Beet<T, V>): FixableBeet<T[], V[]>;
/**
 * A De/Serializer for raw {@link Buffer}s that just copies/reads the buffer bytes
 * to/from the provided buffer.
 *
 * @param bytes the byte size of the buffer to de/serialize
 * @category beet/collection
 */
export declare function fixedSizeBuffer(bytes: number): FixedSizeBeet<Buffer>;
/**
 * A De/Serializer for {@link Uint8Array}s of known size that just copies/reads
 * the array bytes to/from the provided buffer.
 *
 * @category beet/collection
 */
export declare function fixedSizeUint8Array(len: number, lenPrefix?: boolean): FixedSizeBeet<Uint8Array>;
/**
 * A De/Serializer for {@link Uint8Array}s that just copies/reads the array bytes
 * to/from the provided buffer.
 *
 * @category beet/collection
 */
export declare const uint8Array: FixableBeet<Uint8Array, Uint8Array>;
/**
 * @category TypeDefinition
 */
export declare type CollectionsExports = keyof typeof import('./collections');
/**
 * @category TypeDefinition
 */
export declare type CollectionsTypeMapKey = 'Array' | 'FixedSizeArray' | 'UniformFixedSizeArray' | 'Buffer' | 'FixedSizeUint8Array' | 'Uint8Array';
/**
 * @category TypeDefinition
 */
export declare type CollectionsTypeMap = Record<CollectionsTypeMapKey, SupportedTypeDefinition & {
    beet: CollectionsExports;
}>;
/**
 * Maps collections beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const collectionsTypeMap: CollectionsTypeMap;
