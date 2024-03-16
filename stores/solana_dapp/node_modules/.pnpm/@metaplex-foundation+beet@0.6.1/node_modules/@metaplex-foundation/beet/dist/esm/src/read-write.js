import { strict as assert } from 'assert';
/**
 * Underlying writer used to serialize structs.
 *
 * @private
 * @category beet/struct
 */
export class BeetWriter {
    constructor(byteSize) {
        this.buf = Buffer.alloc(byteSize);
        this._offset = 0;
    }
    get buffer() {
        return this.buf;
    }
    get offset() {
        return this._offset;
    }
    maybeResize(bytesNeeded) {
        if (this._offset + bytesNeeded > this.buf.length) {
            assert.fail(`We shouldn't ever need to resize, but ${this._offset + bytesNeeded} > ${this.buf.length}`);
            // this.buf = Buffer.concat([this.buf, Buffer.alloc(this.allocateBytes)])
        }
    }
    write(beet, value) {
        this.maybeResize(beet.byteSize);
        beet.write(this.buf, this._offset, value);
        this._offset += beet.byteSize;
    }
    writeStruct(instance, fields) {
        for (const [key, beet] of fields) {
            const value = instance[key];
            this.write(beet, value);
        }
    }
}
/**
 * Underlying reader used to deserialize structs.
 *
 * @private
 * @category beet/struct
 */
export class BeetReader {
    constructor(buffer, _offset = 0) {
        this.buffer = buffer;
        this._offset = _offset;
    }
    get offset() {
        return this._offset;
    }
    read(beet) {
        const value = beet.read(this.buffer, this._offset);
        this._offset += beet.byteSize;
        return value;
    }
    readStruct(fields) {
        const acc = {};
        for (const [key, beet] of fields) {
            acc[key] = this.read(beet);
        }
        return acc;
    }
}
//# sourceMappingURL=read-write.js.map