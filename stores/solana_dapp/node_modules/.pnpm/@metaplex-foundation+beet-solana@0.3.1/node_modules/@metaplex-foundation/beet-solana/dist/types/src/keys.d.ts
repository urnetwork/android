import { PublicKey } from '@solana/web3.js';
import { FixedSizeBeet, SupportedTypeDefinition } from '@metaplex-foundation/beet';
/**
 * De/Serializer for solana {@link PublicKey}s aka `publicKey`.
 *
 *
 * ## Using PublicKey Directly
 *
 * ```ts
 * import { publicKey } from '@metaplex-foundation/beet-solana'
 *
 * const generatedKey  = Keypair.generate().publicKey
 * const buf = Buffer.alloc(publicKey.byteSize)
 * beet.write(buf, 0, generatedKey)
 * beet.read(buf, 0) // same as generatedKey
 * ```
 *
 * ## PublicKey as part of a Struct Configuration
 *
 * ```ts
 * import { publicKey } from '@metaplex-foundation/beet-solana'
 *
 * type InstructionArgs = {
 *   authority: web3.PublicKey
 * }
 *
 * const createStruct = new beet.BeetArgsStruct<InstructionArgs>(
 *   [
 *     ['authority', publicKey]
 *   ],
 *   'InstructionArgs'
 * )
 * ```
 *
 * @category beet/solana
 */
export declare const publicKey: FixedSizeBeet<PublicKey>;
/**
 * @category TypeDefinition
 */
export declare type KeysExports = keyof typeof import('./keys');
/**
 * @category TypeDefinition
 */
export declare type KeysTypeMapKey = 'publicKey';
/**
 * @category TypeDefinition
 */
export declare type KeysTypeMap = Record<KeysTypeMapKey, SupportedTypeDefinition & {
    beet: KeysExports;
}>;
/**
 * Maps solana keys beet exports to metadata which describes in which package it
 * is defined as well as which TypeScript type is used to represent the
 * deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export declare const keysTypeMap: KeysTypeMap;
