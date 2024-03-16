/// <reference types="node" />
import { FixedSizeBeet, FixedBeetField } from './types';
/**
 * Underlying writer used to serialize structs.
 *
 * @private
 * @category beet/struct
 */
export declare class BeetWriter {
    private buf;
    private _offset;
    constructor(byteSize: number);
    get buffer(): Buffer;
    get offset(): number;
    private maybeResize;
    write<T>(beet: FixedSizeBeet<T>, value: T): void;
    writeStruct<T>(instance: T, fields: FixedBeetField<T>[]): void;
}
/**
 * Underlying reader used to deserialize structs.
 *
 * @private
 * @category beet/struct
 */
export declare class BeetReader {
    private readonly buffer;
    private _offset;
    constructor(buffer: Buffer, _offset?: number);
    get offset(): number;
    read<T>(beet: FixedSizeBeet<T>): T;
    readStruct<T>(fields: FixedBeetField<T>[]): T;
}
