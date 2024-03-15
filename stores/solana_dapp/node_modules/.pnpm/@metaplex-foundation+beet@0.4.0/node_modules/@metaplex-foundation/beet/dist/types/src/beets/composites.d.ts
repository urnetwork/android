/// <reference types="node" />
import { Beet, FixableBeet, FixedSizeBeet, SupportedTypeDefinition } from '../types';
/**
 * Represents the Rust Option type {@link T}.
 *
 * @template T inner option type
 *
 * @category beet/option
 */
export declare type COption<T> = T | null;
/**
 * @private
 */
export declare function isSomeBuffer(buf: Buffer, offset: number): boolean;
/**
 * @private
 */
export declare function isNoneBuffer(buf: Buffer, offset: number): boolean;
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
export declare function coptionNone<T>(description: string): FixedSizeBeet<COption<T>>;
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
export declare function coptionSome<T>(inner: FixedSizeBeet<T>): FixedSizeBeet<COption<T>>;
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
export declare function coption<T, V = T>(inner: Beet<T, V>): FixableBeet<COption<T>>;
/**
 * @category TypeDefinition
 */
export declare type CompositesExports = keyof typeof import('./composites');
/**
 * @category TypeDefinition
 */
export declare type CompositesTypeMapKey = 'option';
/**
 * @category TypeDefinition
 */
export declare type CompositesTypeMap = Record<CompositesTypeMapKey, SupportedTypeDefinition & {
    beet: CompositesExports;
}>;
/**
 * Maps composite beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const compositesTypeMap: CompositesTypeMap;
