"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.eventStreamSerdeProvider = void 0;
const EventStreamMarshaller_1 = require("./EventStreamMarshaller");
const eventStreamSerdeProvider = (options) => new EventStreamMarshaller_1.EventStreamMarshaller(options);
exports.eventStreamSerdeProvider = eventStreamSerdeProvider;
