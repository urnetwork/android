import { bignum, SupportedTypeDefinition, FixedSizeBeet } from '../types';
/**
 * De/Serializer for 8-bit unsigned integers aka `u8`.
 *
 * @category beet/primitive
 */
export declare const u8: FixedSizeBeet<number>;
/**
 * De/Serializer 16-bit unsigned integers aka `u16`.
 *
 * @category beet/primitive
 */
export declare const u16: FixedSizeBeet<number>;
/**
 * De/Serializer for 32-bit unsigned integers aka `u32`.
 *
 * @category beet/primitive
 */
export declare const u32: FixedSizeBeet<number>;
/**
 * De/Serializer for 64-bit unsigned integers aka `u64` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const u64: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 128-bit unsigned integers aka `u128` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const u128: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 256-bit unsigned integers aka `u256` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const u256: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 512-bit unsigned integers aka `u512` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const u512: FixedSizeBeet<bignum>;
/**
 * De/Serializer 8-bit signed integers aka `i8`.
 *
 * @category beet/primitive
 */
export declare const i8: FixedSizeBeet<number>;
/**
 * De/Serializer 16-bit signed integers aka `i16`.
 *
 * @category beet/primitive
 */
export declare const i16: FixedSizeBeet<number>;
/**
 * De/Serializer 32-bit signed integers aka `i32`.
 *
 * @category beet/primitive
 */
export declare const i32: FixedSizeBeet<number>;
/**
 * De/Serializer for 64-bit signed integers aka `i64` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const i64: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 128-bit signed integers aka `i128` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const i128: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 256-bit signed integers aka `i256` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const i256: FixedSizeBeet<bignum>;
/**
 * De/Serializer for 512-bit signed integers aka `i512` which serializes to a JavaScript
 * _BigNum_ via {@link https://github.com/indutny/bn.js | BN}.
 *
 * @category beet/primitive
 */
export declare const i512: FixedSizeBeet<bignum>;
/**
 * De/Serializer booleans aka `bool`.
 *
 * @category beet/primitive
 */
export declare const bool: FixedSizeBeet<boolean>;
/**
 * @category TypeDefinition
 */
export declare type NumbersExports = keyof typeof import('./numbers');
/**
 * @category TypeDefinition
 */
export declare type NumbersTypeMapKey = 'u8' | 'u16' | 'u32' | 'u64' | 'u128' | 'u256' | 'u512' | 'i8' | 'i16' | 'i32' | 'i64' | 'i128' | 'i256' | 'i512' | 'bool';
/**
 * @category TypeDefinition
 */
export declare type NumbersTypeMap = Record<NumbersTypeMapKey, SupportedTypeDefinition & {
    beet: NumbersExports;
}>;
/**
 * Maps primitive beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const numbersTypeMap: NumbersTypeMap;
