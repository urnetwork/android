/**
 * Maps Error names to Error codes
 *
 * @private
 */
export declare const LangErrorCode: {
    InstructionMissing: number;
    InstructionFallbackNotFound: number;
    InstructionDidNotDeserialize: number;
    InstructionDidNotSerialize: number;
    IdlInstructionStub: number;
    IdlInstructionInvalidProgram: number;
    ConstraintMut: number;
    ConstraintHasOne: number;
    ConstraintSigner: number;
    ConstraintRaw: number;
    ConstraintOwner: number;
    ConstraintRentExempt: number;
    ConstraintSeeds: number;
    ConstraintExecutable: number;
    ConstraintState: number;
    ConstraintAssociated: number;
    ConstraintAssociatedInit: number;
    ConstraintClose: number;
    ConstraintAddress: number;
    ConstraintZero: number;
    ConstraintTokenMint: number;
    ConstraintTokenOwner: number;
    ConstraintMintMintAuthority: number;
    ConstraintMintFreezeAuthority: number;
    ConstraintMintDecimals: number;
    ConstraintSpace: number;
    AccountDiscriminatorAlreadySet: number;
    AccountDiscriminatorNotFound: number;
    AccountDiscriminatorMismatch: number;
    AccountDidNotDeserialize: number;
    AccountDidNotSerialize: number;
    AccountNotEnoughKeys: number;
    AccountNotMutable: number;
    AccountOwnedByWrongProgram: number;
    InvalidProgramId: number;
    InvalidProgramExecutable: number;
    AccountNotSigner: number;
    AccountNotSystemOwned: number;
    AccountNotInitialized: number;
    AccountNotProgramData: number;
    StateInvalidAddress: number;
    Deprecated: number;
};
/**
 * Maps Error Codes to Error messages
 * @private
 */
export declare const LangErrorMessage: Map<number, string>;
