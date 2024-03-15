"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.errorCodeFromLogs = void 0;
const errorLineRx = /Custom program error: (0x[a-f0-9]+)/i;
/**
 * Parses an error code included with solana logs
 *
 * @private
 */
function errorCodeFromLogs(logs) {
    for (const line of logs) {
        const match = line.match(errorLineRx);
        if (match == null)
            continue;
        const hexCode = match[1];
        try {
            return parseInt(hexCode);
        }
        catch (_) { }
    }
    return null;
}
exports.errorCodeFromLogs = errorCodeFromLogs;
//# sourceMappingURL=parse-error.js.map