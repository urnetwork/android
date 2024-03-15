import { ErrorMeta, ErrorWithLogs, MaybeErrorWithCode, ResolveErrorFromCode } from './types';
export declare class ErrorResolver {
    private readonly resolveErrorFromCode?;
    constructor(resolveErrorFromCode?: ResolveErrorFromCode | undefined);
    /**
     * Attempts to resolve the provided error code to a known or custom error.
     *
     * @param captureBoundaryFn is used to exclude everything after (including)
     * that function from the stack trace if possible
     * @param fallbackToUnknown unless `false` a {@link CusperUnknownError} is
     * returned when resolution fails
     */
    errorFromCode(code: number, captureBoundaryFn?: Function, fallbackToUnknown?: boolean): MaybeErrorWithCode;
    /**
     * Attempts to parse the error code from the provied logs and then resolve it
     * to a known or custom error.
     * @param fallbackToUnknown unless `false` a {@link CusperUnknownError} is
     * returned when resolution fails
     */
    errorFromProgramLogs(logs: string[], fallbackToUnknown?: boolean): MaybeErrorWithCode;
    /**
     * Throws an error that it attempts to resolve from the logs of the provided error.
     * If no error can be resolved it throws a {@link CusperUnknownError} instead
     */
    throwError(error: ErrorWithLogs): void;
    private passPreparedError;
}
/**
 * Initializes a Custom Program Error Resolver, aka _Cusper_.
 *
 * @param resolveErrorFromCode if provided it will be used to resolve custom
 * errors before falling back to known program errors
 */
export declare function initCusper(resolveErrorFromCode?: ResolveErrorFromCode): ErrorResolver;
/**
 * This error is returned/raised when an error code couldn't be found or resolved to a
 * custom or known error.
 */
export declare class CusperUnknownError extends Error {
    readonly code: number;
    constructor(code: number, ...params: any[]);
}
/**
 * Used by implementers to provide their own errors to be resolved by cusper.
 */
export declare class CustomProgramError extends Error {
    readonly code: number;
    /**
     * Creates an instance of a {@link CustomProgramError}.
     *
     * @param code the error code for which this error was resolved
     * @param name the name of the error
     */
    constructor(code: number, name: string, ...params: any[]);
}
/**
 * An error raised by the anchor program before getting to the actual program
 * implementation.
 */
export declare class AnchorError extends Error {
    readonly code: number;
    constructor(code: number, name: string, ...params: any[]);
    static errorMap: Map<number, ErrorMeta>;
    static fromCode(code: number): MaybeErrorWithCode;
    toString(): string;
}
/**
 * Error raised by the token lending program.
 * Please note that error codes overlap with other _known_ programs as they start at `0`.
 * Thus in some cases they might be wrongly represented and actually not
 * originate from the token lending program.
 */
export declare class TokenLendingError extends Error {
    readonly code: number;
    constructor(code: number, name: string, ...params: any[]);
    static errorMap: Map<number, ErrorMeta>;
    static fromCode(code: number): MaybeErrorWithCode;
    toString(): string;
}
