/**
 * Error metadata used internally to create errors
 */
export declare type ErrorMeta = {
    code: number;
    name: string;
    message: string;
};
/**
 * Error that includes a code property.
 */
export declare type ErrorWithCode = Error & {
    code: number;
};
/**
 * Error that includes a `logs` property as is provided by `@solana/web3.js`.
 */
export declare type ErrorWithLogs = Error & {
    logs: string[];
};
/**
 * Possibly {@link ErrorWithCode} or None.
 */
export declare type MaybeErrorWithCode = ErrorWithCode | null | undefined;
/**
 * Function to be provided to the {@link initCusper} in order to resolve custom
 * program errors.
 */
export declare type ResolveErrorFromCode = (code: number) => ErrorWithCode | null | undefined;
