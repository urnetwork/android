/// <reference types="node" />
import { BeetField } from '@metaplex-foundation/beet';
import { Connection, GetProgramAccountsConfig, PublicKey } from '@solana/web3.js';
/**
 * Provides an Account specific GPA builder.
 *
 * @template T - the type of the account for which the GpaBuilder is used
 */
export declare class GpaBuilder<T> {
    private readonly programId;
    private readonly beets;
    private readonly accountSize;
    /**
     * web3.js {@link GetProgramAccountsConfig} configured via filter GpaBuilder filter methods.
     */
    readonly config: GetProgramAccountsConfig;
    private constructor();
    private _addFilter;
    private _addInnerFilter;
    /**
     * Adds a _memcmp_ filter for a field inside a field which is a struct value.
     * The provided keys need to be separated by a `.` and only one level of
     * nesting is supported at this point.
     *
     * The filter is applied to the inner value.
     *
     * ## Example
     *
     * ### Given:
     *
     * ```typescript
     * type Inner = {
     *   a: number
     * }
     * type Outer = {
     *   idx: number
     *   inner: Inner
     * }
     * ```
     * ### Apply a filter on `a` of the `Inner` type:
     *
     * ```typescript
     * gpaBuilder.addInnerFilter('inner.a', 2)
     * ```
     *
     * @param keys - the names of the fields by which to filter, i.e. `'outer.inner'`
     * @param val - the field value that the filter should match
     */
    addInnerFilter(keys: string, val: T[keyof T] & {}): this;
    /**
     * Adds a _memcmp_ filter for the provided {@link key} of the struct.
     *
     * @param key - the name of the field by which to filter
     * @param val - the field value that the filter should match
     */
    addFilter(key: keyof T & string, val: T[keyof T]): this;
    /**
     * Adds a `dataSize` filter which will match on account's sizes.
     * You have to provide that {@link size} for accounts that don't have a fixed size.
     * For _fixed_ size accounts that size is determined for you.
     *
     * @param size - the account size to match for
     */
    dataSize(size?: number): this;
    /**
     * Attempts to find the accounts matching the configured filters.
     *
     * @param connection used to query the program accounts on the cluster
     */
    run(connection: Connection): Promise<{
        pubkey: PublicKey;
        account: import("@solana/web3.js").AccountInfo<Buffer>;
    }[]>;
    /**
     * Creates a GPA builder that supports adding up to four filters for
     * fixed size fields.
     *
     * Once a non-fixed field is encountered, the remaining fields following it
     * will not be included as a filter option since their position in the
     * bytes array will change depending on the content of the non-fixed field.
     *
     * @param programId - the id of the program that owns the accounts we are querying
     * @param beetFields - the beet fields that make up the structure of the account data
     */
    static fromBeetFields<T>(programId: PublicKey, beetFields: BeetField<T, T[keyof T]>[]): GpaBuilder<T>;
    /**
     * Convenience wrapper around {@link GpaBuilder.fromBeetFields} that allows
     * providing a struct which contains the beet fields.
     *
     * @param programId - the id of the program that owns the accounts we are querying
     * @param struct - containing the beet `fields` specifying the layout of the account
     */
    static fromStruct<T>(programId: PublicKey, struct: {
        fields: BeetField<T, T[keyof T]>[];
    }): GpaBuilder<T>;
}
