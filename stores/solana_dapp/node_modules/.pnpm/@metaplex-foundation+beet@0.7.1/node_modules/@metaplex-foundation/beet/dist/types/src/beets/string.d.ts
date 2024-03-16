import { FixedSizeBeet, FixableBeet, SupportedTypeDefinition } from '../types';
/**
 * De/Serializes a UTF8 string of a particular size.
 *
 * @param stringByteLength the number of bytes of the string
 *
 * @category beet/collection
 */
export declare const fixedSizeUtf8String: (stringByteLength: number) => FixedSizeBeet<string, string>;
/**
 * De/Serializes a UTF8 string of any size.
 *
 * @category beet/collection
 */
export declare const utf8String: FixableBeet<string, string>;
/**
 * @category TypeDefinition
 */
export declare type StringExports = keyof typeof import('./string');
/**
 * @category TypeDefinition
 */
export declare type StringTypeMapKey = 'string' | 'fixedSizeString';
/**
 * @category TypeDefinition
 */
export declare type StringTypeMap = Record<StringTypeMapKey, SupportedTypeDefinition & {
    beet: StringExports;
}>;
/**
 * Maps string beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const stringTypeMap: StringTypeMap;
