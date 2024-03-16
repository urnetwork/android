import base58 from 'bs58';
export function encodeFixedBeet(beet, val) {
    const buf = Buffer.alloc(beet.byteSize);
    beet.write(buf, 0, val);
    return base58.encode(buf);
}
//# sourceMappingURL=util.js.map