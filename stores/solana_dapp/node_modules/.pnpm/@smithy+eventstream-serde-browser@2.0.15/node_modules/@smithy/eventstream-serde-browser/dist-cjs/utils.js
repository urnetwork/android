"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.iterableToReadableStream = exports.readableStreamtoIterable = void 0;
const readableStreamtoIterable = (readableStream) => ({
    [Symbol.asyncIterator]: async function* () {
        const reader = readableStream.getReader();
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done)
                    return;
                yield value;
            }
        }
        finally {
            reader.releaseLock();
        }
    },
});
exports.readableStreamtoIterable = readableStreamtoIterable;
const iterableToReadableStream = (asyncIterable) => {
    const iterator = asyncIterable[Symbol.asyncIterator]();
    return new ReadableStream({
        async pull(controller) {
            const { done, value } = await iterator.next();
            if (done) {
                return controller.close();
            }
            controller.enqueue(value);
        },
    });
};
exports.iterableToReadableStream = iterableToReadableStream;
