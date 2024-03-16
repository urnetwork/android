import { FixableBeet, SupportedTypeDefinition } from '../types';
/**
 * Alias for {@link uint8Array}.
 * @category TypeDefinition
 */
export declare const bytes: FixableBeet<Uint8Array, Uint8Array>;
/**
 * @category TypeDefinition
 */
export declare type AliasesExports = keyof typeof import('./aliases');
/**
 * @category TypeDefinition
 */
export declare type AliasesTypeMapKey = 'Uint8Array';
/**
 * @category TypeDefinition
 */
export declare type AliasesTypeMap = Record<AliasesTypeMapKey, SupportedTypeDefinition & {
    beet: AliasesExports;
}>;
export declare const aliasesTypeMap: AliasesTypeMap;
