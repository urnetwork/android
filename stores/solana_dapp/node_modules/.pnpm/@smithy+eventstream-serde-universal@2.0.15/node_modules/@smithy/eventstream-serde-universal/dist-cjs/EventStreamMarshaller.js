"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EventStreamMarshaller = void 0;
const eventstream_codec_1 = require("@smithy/eventstream-codec");
const getChunkedStream_1 = require("./getChunkedStream");
const getUnmarshalledStream_1 = require("./getUnmarshalledStream");
class EventStreamMarshaller {
    constructor({ utf8Encoder, utf8Decoder }) {
        this.eventStreamCodec = new eventstream_codec_1.EventStreamCodec(utf8Encoder, utf8Decoder);
        this.utfEncoder = utf8Encoder;
    }
    deserialize(body, deserializer) {
        const inputStream = (0, getChunkedStream_1.getChunkedStream)(body);
        return new eventstream_codec_1.SmithyMessageDecoderStream({
            messageStream: new eventstream_codec_1.MessageDecoderStream({ inputStream, decoder: this.eventStreamCodec }),
            deserializer: (0, getUnmarshalledStream_1.getMessageUnmarshaller)(deserializer, this.utfEncoder),
        });
    }
    serialize(inputStream, serializer) {
        return new eventstream_codec_1.MessageEncoderStream({
            messageStream: new eventstream_codec_1.SmithyMessageEncoderStream({ inputStream, serializer }),
            encoder: this.eventStreamCodec,
            includeEndFrame: true,
        });
    }
}
exports.EventStreamMarshaller = EventStreamMarshaller;
