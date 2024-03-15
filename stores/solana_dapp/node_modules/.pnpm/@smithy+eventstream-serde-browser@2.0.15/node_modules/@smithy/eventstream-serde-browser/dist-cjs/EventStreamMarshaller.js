"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EventStreamMarshaller = void 0;
const eventstream_serde_universal_1 = require("@smithy/eventstream-serde-universal");
const utils_1 = require("./utils");
class EventStreamMarshaller {
    constructor({ utf8Encoder, utf8Decoder }) {
        this.universalMarshaller = new eventstream_serde_universal_1.EventStreamMarshaller({
            utf8Decoder,
            utf8Encoder,
        });
    }
    deserialize(body, deserializer) {
        const bodyIterable = isReadableStream(body) ? (0, utils_1.readableStreamtoIterable)(body) : body;
        return this.universalMarshaller.deserialize(bodyIterable, deserializer);
    }
    serialize(input, serializer) {
        const serialziedIterable = this.universalMarshaller.serialize(input, serializer);
        return typeof ReadableStream === "function" ? (0, utils_1.iterableToReadableStream)(serialziedIterable) : serialziedIterable;
    }
}
exports.EventStreamMarshaller = EventStreamMarshaller;
const isReadableStream = (body) => typeof ReadableStream === "function" && body instanceof ReadableStream;
