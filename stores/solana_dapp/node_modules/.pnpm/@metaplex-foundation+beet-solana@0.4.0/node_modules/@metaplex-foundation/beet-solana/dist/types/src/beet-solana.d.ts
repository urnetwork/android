import { SupportedTypeDefinition } from '@metaplex-foundation/beet';
import { KeysExports, KeysTypeMapKey } from './keys';
export * from './keys';
export * from './gpa';
/**
 * @category TypeDefinition
 */
export declare type BeetSolanaTypeMapKey = KeysTypeMapKey;
/**
 * @category TypeDefinition
 */
export declare type BeetSolanaExports = KeysExports;
/**
 * Maps solana beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const supportedTypeMap: Record<BeetSolanaTypeMapKey, SupportedTypeDefinition & {
    beet: BeetSolanaExports;
}>;
