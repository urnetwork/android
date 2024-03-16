"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() { return m[k]; } });
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TokenLendingError = exports.AnchorError = exports.CustomProgramError = exports.CusperUnknownError = exports.initCusper = exports.ErrorResolver = void 0;
const parse_error_1 = require("./parse-error");
const anchor = __importStar(require("./errors/anchor"));
const token_lending_1 = require("./errors/token-lending");
// -----------------
// Error Resolver
// -----------------
class ErrorResolver {
    constructor(resolveErrorFromCode) {
        this.resolveErrorFromCode = resolveErrorFromCode;
    }
    /**
     * Attempts to resolve the provided error code to a known or custom error.
     *
     * @param captureBoundaryFn is used to exclude everything after (including)
     * that function from the stack trace if possible
     * @param fallbackToUnknown unless `false` a {@link CusperUnknownError} is
     * returned when resolution fails
     */
    errorFromCode(code, captureBoundaryFn, fallbackToUnknown = true) {
        // Try specific program errors first since they're more likely
        let err = this.resolveErrorFromCode != null ? this.resolveErrorFromCode(code) : null;
        if (err != null) {
            return this.passPreparedError(err, captureBoundaryFn !== null && captureBoundaryFn !== void 0 ? captureBoundaryFn : this.errorFromCode);
        }
        // Then try errors of known programs
        err = AnchorError.fromCode(code);
        if (err != null) {
            return this.passPreparedError(err, captureBoundaryFn !== null && captureBoundaryFn !== void 0 ? captureBoundaryFn : this.errorFromCode);
        }
        err = TokenLendingError.fromCode(code);
        if (err != null) {
            return this.passPreparedError(err, captureBoundaryFn !== null && captureBoundaryFn !== void 0 ? captureBoundaryFn : this.errorFromCode);
        }
        if (fallbackToUnknown) {
            err = new CusperUnknownError(code, 'CusperUnknownError', 'cusper does not know this error');
            return this.passPreparedError(err, captureBoundaryFn !== null && captureBoundaryFn !== void 0 ? captureBoundaryFn : this.errorFromCode);
        }
    }
    /**
     * Attempts to parse the error code from the provied logs and then resolve it
     * to a known or custom error.
     * @param fallbackToUnknown unless `false` a {@link CusperUnknownError} is
     * returned when resolution fails
     */
    errorFromProgramLogs(logs, fallbackToUnknown = true) {
        const code = (0, parse_error_1.errorCodeFromLogs)(logs);
        return code == null
            ? null
            : this.errorFromCode(code, this.errorFromProgramLogs, fallbackToUnknown);
    }
    /**
     * Throws an error that it attempts to resolve from the logs of the provided error.
     * If no error can be resolved it throws a {@link CusperUnknownError} instead
     */
    throwError(error) {
        const err = (error.logs != null && this.errorFromProgramLogs(error.logs, true)) ||
            new CusperUnknownError(-1, 'Error created without logs and thus without error code');
        throw this.passPreparedError(err, this.throwError);
    }
    passPreparedError(err, captureBoundaryFn) {
        if (err == null)
            return null;
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(err, captureBoundaryFn);
        }
        return err;
    }
}
exports.ErrorResolver = ErrorResolver;
/**
 * Initializes a Custom Program Error Resolver, aka _Cusper_.
 *
 * @param resolveErrorFromCode if provided it will be used to resolve custom
 * errors before falling back to known program errors
 */
function initCusper(resolveErrorFromCode) {
    return new ErrorResolver(resolveErrorFromCode);
}
exports.initCusper = initCusper;
// -----------------
// Unknown Error
// -----------------
/**
 * This error is returned/raised when an error code couldn't be found or resolved to a
 * custom or known error.
 */
class CusperUnknownError extends Error {
    constructor(code, ...params) {
        super(...params);
        this.code = code;
        this.name = 'CusperUnknownError';
    }
}
exports.CusperUnknownError = CusperUnknownError;
// -----------------
// Custom Program Error
// -----------------
/**
 * Used by implementers to provide their own errors to be resolved by cusper.
 */
class CustomProgramError extends Error {
    /**
     * Creates an instance of a {@link CustomProgramError}.
     *
     * @param code the error code for which this error was resolved
     * @param name the name of the error
     */
    constructor(code, name, ...params) {
        super(...params);
        this.code = code;
        this.name = `CustomProgramError#${name}`;
    }
}
exports.CustomProgramError = CustomProgramError;
// -----------------
// Anchor
// -----------------
/**
 * An error raised by the anchor program before getting to the actual program
 * implementation.
 */
class AnchorError extends Error {
    constructor(code, name, ...params) {
        super(...params);
        this.code = code;
        this.name = `AnchorError#${name}`;
    }
    static fromCode(code) {
        const errorMeta = AnchorError.errorMap.get(code);
        return errorMeta != null
            ? new AnchorError(errorMeta.code, errorMeta.name, errorMeta.message)
            : null;
    }
    toString() {
        return `${this.name}: ${this.message}`;
    }
}
exports.AnchorError = AnchorError;
AnchorError.errorMap = Object.entries(anchor.LangErrorCode).reduce((acc, [key, code]) => {
    acc.set(code, {
        code,
        name: key,
        message: anchor.LangErrorMessage.get(code),
    });
    return acc;
}, new Map());
// -----------------
// Token Lending
// -----------------
/**
 * Error raised by the token lending program.
 * Please note that error codes overlap with other _known_ programs as they start at `0`.
 * Thus in some cases they might be wrongly represented and actually not
 * originate from the token lending program.
 */
class TokenLendingError extends Error {
    constructor(code, name, ...params) {
        super(...params);
        this.code = code;
        this.name = `TokenLendingError#${name}`;
    }
    static fromCode(code) {
        const errorMeta = TokenLendingError.errorMap.get(code);
        return errorMeta != null
            ? new TokenLendingError(errorMeta.code, errorMeta.name, errorMeta.message)
            : null;
    }
    toString() {
        return `${this.name}: ${this.message}`;
    }
}
exports.TokenLendingError = TokenLendingError;
TokenLendingError.errorMap = token_lending_1.tokenLendingErrors;
//# sourceMappingURL=resolve-error.js.map