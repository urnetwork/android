"use strict";
// https://github.com/project-serum/anchor/blob/1749a7bd53eec516416a61598290da1431e2910f/ts/src/error.ts
Object.defineProperty(exports, "__esModule", { value: true });
exports.LangErrorMessage = exports.LangErrorCode = void 0;
/**
 * Maps Error names to Error codes
 *
 * @private
 */
exports.LangErrorCode = {
    // Instructions.
    InstructionMissing: 100,
    InstructionFallbackNotFound: 101,
    InstructionDidNotDeserialize: 102,
    InstructionDidNotSerialize: 103,
    // IDL instructions.
    IdlInstructionStub: 1000,
    IdlInstructionInvalidProgram: 1001,
    // Constraints.
    ConstraintMut: 2000,
    ConstraintHasOne: 2001,
    ConstraintSigner: 2002,
    ConstraintRaw: 2003,
    ConstraintOwner: 2004,
    ConstraintRentExempt: 2005,
    ConstraintSeeds: 2006,
    ConstraintExecutable: 2007,
    ConstraintState: 2008,
    ConstraintAssociated: 2009,
    ConstraintAssociatedInit: 2010,
    ConstraintClose: 2011,
    ConstraintAddress: 2012,
    ConstraintZero: 2013,
    ConstraintTokenMint: 2014,
    ConstraintTokenOwner: 2015,
    ConstraintMintMintAuthority: 2016,
    ConstraintMintFreezeAuthority: 2017,
    ConstraintMintDecimals: 2018,
    ConstraintSpace: 2019,
    // Accounts.
    AccountDiscriminatorAlreadySet: 3000,
    AccountDiscriminatorNotFound: 3001,
    AccountDiscriminatorMismatch: 3002,
    AccountDidNotDeserialize: 3003,
    AccountDidNotSerialize: 3004,
    AccountNotEnoughKeys: 3005,
    AccountNotMutable: 3006,
    AccountOwnedByWrongProgram: 3007,
    InvalidProgramId: 3008,
    InvalidProgramExecutable: 3009,
    AccountNotSigner: 3010,
    AccountNotSystemOwned: 3011,
    AccountNotInitialized: 3012,
    AccountNotProgramData: 3013,
    // State.
    StateInvalidAddress: 4000,
    // Used for APIs that shouldn't be used anymore.
    Deprecated: 5000,
};
/**
 * Maps Error Codes to Error messages
 * @private
 */
exports.LangErrorMessage = new Map([
    // Instructions.
    [
        exports.LangErrorCode.InstructionMissing,
        '8 byte instruction identifier not provided',
    ],
    [
        exports.LangErrorCode.InstructionFallbackNotFound,
        'Fallback functions are not supported',
    ],
    [
        exports.LangErrorCode.InstructionDidNotDeserialize,
        'The program could not deserialize the given instruction',
    ],
    [
        exports.LangErrorCode.InstructionDidNotSerialize,
        'The program could not serialize the given instruction',
    ],
    // Idl instructions.
    [
        exports.LangErrorCode.IdlInstructionStub,
        'The program was compiled without idl instructions',
    ],
    [
        exports.LangErrorCode.IdlInstructionInvalidProgram,
        'The transaction was given an invalid program for the IDL instruction',
    ],
    // Constraints.
    [exports.LangErrorCode.ConstraintMut, 'A mut constraint was violated'],
    [exports.LangErrorCode.ConstraintHasOne, 'A has_one constraint was violated'],
    [exports.LangErrorCode.ConstraintSigner, 'A signer constraint was violated'],
    [exports.LangErrorCode.ConstraintRaw, 'A raw constraint was violated'],
    [exports.LangErrorCode.ConstraintOwner, 'An owner constraint was violated'],
    [exports.LangErrorCode.ConstraintRentExempt, 'A rent exempt constraint was violated'],
    [exports.LangErrorCode.ConstraintSeeds, 'A seeds constraint was violated'],
    [exports.LangErrorCode.ConstraintExecutable, 'An executable constraint was violated'],
    [exports.LangErrorCode.ConstraintState, 'A state constraint was violated'],
    [exports.LangErrorCode.ConstraintAssociated, 'An associated constraint was violated'],
    [
        exports.LangErrorCode.ConstraintAssociatedInit,
        'An associated init constraint was violated',
    ],
    [exports.LangErrorCode.ConstraintClose, 'A close constraint was violated'],
    [exports.LangErrorCode.ConstraintAddress, 'An address constraint was violated'],
    [exports.LangErrorCode.ConstraintZero, 'Expected zero account discriminant'],
    [exports.LangErrorCode.ConstraintTokenMint, 'A token mint constraint was violated'],
    [exports.LangErrorCode.ConstraintTokenOwner, 'A token owner constraint was violated'],
    [
        exports.LangErrorCode.ConstraintMintMintAuthority,
        'A mint mint authority constraint was violated',
    ],
    [
        exports.LangErrorCode.ConstraintMintFreezeAuthority,
        'A mint freeze authority constraint was violated',
    ],
    [
        exports.LangErrorCode.ConstraintMintDecimals,
        'A mint decimals constraint was violated',
    ],
    [exports.LangErrorCode.ConstraintSpace, 'A space constraint was violated'],
    // Accounts.
    [
        exports.LangErrorCode.AccountDiscriminatorAlreadySet,
        'The account discriminator was already set on this account',
    ],
    [
        exports.LangErrorCode.AccountDiscriminatorNotFound,
        'No 8 byte discriminator was found on the account',
    ],
    [
        exports.LangErrorCode.AccountDiscriminatorMismatch,
        '8 byte discriminator did not match what was expected',
    ],
    [exports.LangErrorCode.AccountDidNotDeserialize, 'Failed to deserialize the account'],
    [exports.LangErrorCode.AccountDidNotSerialize, 'Failed to serialize the account'],
    [
        exports.LangErrorCode.AccountNotEnoughKeys,
        'Not enough account keys given to the instruction',
    ],
    [exports.LangErrorCode.AccountNotMutable, 'The given account is not mutable'],
    [
        exports.LangErrorCode.AccountOwnedByWrongProgram,
        'The given account is owned by a different program than expected',
    ],
    [exports.LangErrorCode.InvalidProgramId, 'Program ID was not as expected'],
    [exports.LangErrorCode.InvalidProgramExecutable, 'Program account is not executable'],
    [exports.LangErrorCode.AccountNotSigner, 'The given account did not sign'],
    [
        exports.LangErrorCode.AccountNotSystemOwned,
        'The given account is not owned by the system program',
    ],
    [
        exports.LangErrorCode.AccountNotInitialized,
        'The program expected this account to be already initialized',
    ],
    [
        exports.LangErrorCode.AccountNotProgramData,
        'The given account is not a program data account',
    ],
    // State.
    [
        exports.LangErrorCode.StateInvalidAddress,
        'The given state account does not have the correct address',
    ],
    // Misc.
    [
        exports.LangErrorCode.Deprecated,
        'The API being used is deprecated and should no longer be used',
    ],
]);
//# sourceMappingURL=anchor.js.map