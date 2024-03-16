/// <reference types="node" />
import { BeetStruct } from './struct';
import { BeetField, FixableBeet } from './types';
/**
 * Configures a class or any JavaScript object type for de/serialization aka
 * read/write. Not all fields of that class have to be of fixed size.
 * If none of the fields requires a {@link FixableBeet} use the {@link BeetStruct} instead.
 *
 * @template Class the type to produce when deserializing
 * @template Args contains all fields, is typically a subset of Class and is
 * used to construct an instance of it
 *
 * @category beet/struct
 */
export declare class FixableBeetStruct<Class, Args = Partial<Class>> implements FixableBeet<Class, Args> {
    readonly fields: BeetField<Args, any>[];
    private readonly construct;
    readonly description: string;
    /**
     * Creates an instance of the {@link FixableBeetStruct}.
     *
     * @param fields fixed or fixable de/serializers for each field of the {@link Class}
     * @param construct the function that creates an instance of {@link Class}
     * from the args
     * @param description identifies this struct for diagnostics/debugging
     * purposes
     */
    constructor(fields: BeetField<Args, any>[], construct: (args: Args) => Class, description?: string);
    /**
     * Deserializes an instance of the Class from the provided buffer starting to
     * read at the provided offset.
     *
     * @returns `[instance of Class, offset into buffer after deserialization completed]`
     */
    deserialize(buffer: Buffer, offset?: number): [Class, number];
    /**
     * Serializes the provided instance into a new {@link Buffer}
     *
     * **NOTE:** that the `instance` is traversed and each of its fields accessed
     * twice, once to derive a _fixed size_ {@link BeetStruct} and then use it to
     * serialize the `instance`.
     * Therefore ensure that none of the properties that are part of the struct
     * have side effects, i.e. via `Getter`s.
     *
     * @param instance of the struct to serialize
     * @param byteSize allows to override the size fo the created Buffer and
     * defaults to the size of the struct to serialize
     */
    serialize(instance: Args, byteSize?: number): [Buffer, number];
    toFixedFromData(buf: Buffer, offset: number): BeetStruct<Class, Args>;
    toFixedFromValue(args: Args): BeetStruct<Class, Args>;
    static description: string;
    static TYPE: string;
    get type(): string;
}
export declare function isFixableBeetStruct(beet: any): beet is FixableBeetStruct<any, any>;
/**
 * Convenience wrapper around {@link FixableBeetStruct} which is used for plain JavasScript
 * objects, like are used for option args passed to functions.
 *
 * @category beet/struct
 */
export declare class FixableBeetArgsStruct<Args> extends FixableBeetStruct<Args, Args> {
    constructor(fields: BeetField<Args, any>[], description?: string);
    static description: string;
}
