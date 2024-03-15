"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EventStreamMarshaller = void 0;
const eventstream_serde_universal_1 = require("@smithy/eventstream-serde-universal");
const stream_1 = require("stream");
const utils_1 = require("./utils");
class EventStreamMarshaller {
    constructor({ utf8Encoder, utf8Decoder }) {
        this.universalMarshaller = new eventstream_serde_universal_1.EventStreamMarshaller({
            utf8Decoder,
            utf8Encoder,
        });
    }
    deserialize(body, deserializer) {
        const bodyIterable = typeof body[Symbol.asyncIterator] === "function" ? body : (0, utils_1.readabletoIterable)(body);
        return this.universalMarshaller.deserialize(bodyIterable, deserializer);
    }
    serialize(input, serializer) {
        return stream_1.Readable.from(this.universalMarshaller.serialize(input, serializer));
    }
}
exports.EventStreamMarshaller = EventStreamMarshaller;
