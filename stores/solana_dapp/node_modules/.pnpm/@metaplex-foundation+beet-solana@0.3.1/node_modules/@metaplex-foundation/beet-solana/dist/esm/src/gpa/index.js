import { isFixedSizeBeet, } from '@metaplex-foundation/beet';
import { strict as assert } from 'assert';
import { logTrace } from '../utils';
import { encodeFixedBeet } from './util';
/**
 * Provides an Account specific GPA builder.
 *
 * @template T - the type of the account for which the GpaBuilder is used
 */
export class GpaBuilder {
    constructor(programId, beets, accountSize) {
        this.programId = programId;
        this.beets = beets;
        this.accountSize = accountSize;
        /**
         * web3.js {@link GetProgramAccountsConfig} configured via filter GpaBuilder filter methods.
         */
        this.config = {};
    }
    _addFilter(filter) {
        if (this.config.filters == null) {
            this.config.filters = [];
        }
        this.config.filters.push(filter);
        return this;
    }
    _addInnerFilter(key, innerKey, val) {
        logTrace(`gpa.addInnerFilter: ${key}.${innerKey}`);
        const outerBeetInfo = this.beets.get(key);
        assert(outerBeetInfo != null, 'Outer filter key needs to be an existing field name');
        const beetInfo = outerBeetInfo.beet;
        let offset = outerBeetInfo.offset;
        const outerBeet = isFixedSizeBeet(beetInfo)
            ? beetInfo
            : beetInfo.toFixedFromValue(val);
        let beet;
        for (const [k, v] of outerBeet.fields) {
            if (k === innerKey) {
                beet = v;
                break;
            }
            offset += v.byteSize;
        }
        assert(beet != null, `${innerKey} is not a field of the ${key} struct`);
        const bytes = encodeFixedBeet(beet, val);
        this._addFilter({ memcmp: { offset, bytes } });
        return this;
    }
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
    addInnerFilter(keys, val) {
        const parts = keys.split('.');
        assert.equal(parts.length, 2, `inner filters can go only one level deep, i.e. 'outer.inner' is ok, but 'outer.inner.deep' is not`);
        const [ka, kb] = parts;
        return this._addInnerFilter(ka, kb, val);
    }
    /**
     * Adds a _memcmp_ filter for the provided {@link key} of the struct.
     *
     * @param key - the name of the field by which to filter
     * @param val - the field value that the filter should match
     */
    addFilter(key, val) {
        const beetInfo = this.beets.get(key);
        assert(beetInfo != null, 'Filter key needs to be an existing field name');
        const beet = isFixedSizeBeet(beetInfo.beet)
            ? beetInfo.beet
            : beetInfo.beet.toFixedFromValue(val);
        const bytes = encodeFixedBeet(beet, val);
        this._addFilter({ memcmp: { offset: beetInfo.offset, bytes } });
        return this;
    }
    /**
     * Adds a `dataSize` filter which will match on account's sizes.
     * You have to provide that {@link size} for accounts that don't have a fixed size.
     * For _fixed_ size accounts that size is determined for you.
     *
     * @param size - the account size to match for
     */
    dataSize(size) {
        size = size !== null && size !== void 0 ? size : this.accountSize;
        assert(size != null, 'for accounts of dynamic size the dataSize arg needs to be provided');
        return this._addFilter({ dataSize: size });
    }
    /**
     * Attempts to find the accounts matching the configured filters.
     *
     * @param connection used to query the program accounts on the cluster
     */
    run(connection) {
        return connection.getProgramAccounts(this.programId, this.config);
    }
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
    static fromBeetFields(programId, beetFields) {
        const map = new Map();
        let offset = 0;
        let encounteredNonFixed = false;
        for (const [k, v] of beetFields) {
            map.set(k, { beet: v, offset });
            if (!isFixedSizeBeet(v)) {
                encounteredNonFixed = true;
                break;
            }
            offset += v.byteSize;
        }
        const accountSize = encounteredNonFixed ? undefined : offset;
        return new GpaBuilder(programId, map, accountSize);
    }
    /**
     * Convenience wrapper around {@link GpaBuilder.fromBeetFields} that allows
     * providing a struct which contains the beet fields.
     *
     * @param programId - the id of the program that owns the accounts we are querying
     * @param struct - containing the beet `fields` specifying the layout of the account
     */
    static fromStruct(programId, struct) {
        return GpaBuilder.fromBeetFields(programId, struct.fields);
    }
}
//# sourceMappingURL=index.js.map