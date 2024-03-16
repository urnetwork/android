"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.FixableBeetArgsStruct = exports.isFixableBeetStruct = exports.FixableBeetStruct = void 0;
const beet_fixable_1 = require("./beet.fixable");
const struct_1 = require("./struct");
const types_1 = require("./types");
const assert_1 = require("assert");
const utils_1 = require("./utils");
const ansicolors_1 = __importDefault(require("ansicolors"));
const { brightBlack } = ansicolors_1.default;
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
class FixableBeetStruct {
    /**
     * Creates an instance of the {@link FixableBeetStruct}.
     *
     * @param fields fixed or fixable de/serializers for each field of the {@link Class}
     * @param construct the function that creates an instance of {@link Class}
     * from the args
     * @param description identifies this struct for diagnostics/debugging
     * purposes
     */
    constructor(fields, construct, description = FixableBeetStruct.description) {
        this.fields = fields;
        this.construct = construct;
        this.description = description;
        let minByteSize = 0;
        if (utils_1.logDebug.enabled) {
            const flds = fields
                .map(([key, val]) => {
                if ((0, types_1.isFixedSizeBeet)(val)) {
                    minByteSize += val.byteSize;
                }
                return `${key}: ${val.description} ${(0, utils_1.beetBytes)(val)}`;
            })
                .join('\n  ');
            const bytes = `> ${minByteSize} B`;
            (0, utils_1.logDebug)(`struct ${description} {\n  ${flds}\n} ${brightBlack(bytes)}`);
        }
    }
    /**
     * Deserializes an instance of the Class from the provided buffer starting to
     * read at the provided offset.
     *
     * @returns `[instance of Class, offset into buffer after deserialization completed]`
     */
    deserialize(buffer, offset = 0) {
        return this.toFixedFromData(buffer, offset).deserialize(buffer, offset);
    }
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
    serialize(instance, byteSize) {
        return this.toFixedFromValue(instance).serialize(instance, byteSize);
    }
    toFixedFromData(buf, offset) {
        let cursor = offset;
        const fixedFields = new Array(this.fields.length);
        for (let i = 0; i < this.fields.length; i++) {
            const [key, beet] = this.fields[i];
            const fixedBeet = (0, beet_fixable_1.fixBeetFromData)(beet, buf, cursor);
            fixedFields[i] = [key, fixedBeet];
            cursor += fixedBeet.byteSize;
        }
        return this.description !== FixableBeetStruct.description
            ? new struct_1.BeetStruct(fixedFields, this.construct, this.description)
            : new struct_1.BeetStruct(fixedFields, this.construct);
    }
    toFixedFromValue(args) {
        const argsKeys = Object.keys(args);
        const fixedFields = new Array(this.fields.length);
        for (let i = 0; i < this.fields.length; i++) {
            const [key, beet] = this.fields[i];
            (0, assert_1.strict)(argsKeys.includes(key), `Value with keys [ ${argsKeys} ] should include struct key '${key}' but doesn't.`);
            const val = args[key];
            const fixedBeet = (0, beet_fixable_1.fixBeetFromValue)(beet, val);
            fixedFields[i] = [key, fixedBeet];
        }
        return this.description !== FixableBeetStruct.description
            ? new struct_1.BeetStruct(fixedFields, this.construct, this.description)
            : new struct_1.BeetStruct(fixedFields, this.construct);
    }
    get type() {
        return FixableBeetStruct.TYPE;
    }
}
exports.FixableBeetStruct = FixableBeetStruct;
FixableBeetStruct.description = 'FixableBeetStruct';
FixableBeetStruct.TYPE = 'FixableBeetStruct';
function isFixableBeetStruct(beet) {
    return beet.type === FixableBeetStruct.TYPE;
}
exports.isFixableBeetStruct = isFixableBeetStruct;
/**
 * Convenience wrapper around {@link FixableBeetStruct} which is used for plain JavasScript
 * objects, like are used for option args passed to functions.
 *
 * @category beet/struct
 */
class FixableBeetArgsStruct extends FixableBeetStruct {
    constructor(fields, description = FixableBeetArgsStruct.description) {
        super(fields, (args) => args, description);
    }
}
exports.FixableBeetArgsStruct = FixableBeetArgsStruct;
FixableBeetArgsStruct.description = 'FixableBeetArgsStruct';
//# sourceMappingURL=struct.fixable.js.map