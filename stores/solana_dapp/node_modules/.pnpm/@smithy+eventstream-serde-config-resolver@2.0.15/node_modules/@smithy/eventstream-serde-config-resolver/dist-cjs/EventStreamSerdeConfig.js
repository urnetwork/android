"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveEventStreamSerdeConfig = void 0;
const resolveEventStreamSerdeConfig = (input) => ({
    ...input,
    eventStreamMarshaller: input.eventStreamSerdeProvider(input),
});
exports.resolveEventStreamSerdeConfig = resolveEventStreamSerdeConfig;
