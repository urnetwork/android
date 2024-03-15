"use strict";
// Generated via ./scripts/fetch-token-lending-errors.ts
Object.defineProperty(exports, "__esModule", { value: true });
exports.tokenLendingErrors = void 0;
/**
 * Maps error codes to {@link ErrorMeta}.
 * @private
 */
exports.tokenLendingErrors = new Map([
    [
        0,
        {
            code: 0,
            message: 'Failed to unpack instruction data"',
            name: 'InstructionUnpackError',
        },
    ],
    [
        1,
        {
            code: 1,
            message: 'Account is already initialized"',
            name: 'AlreadyInitialized',
        },
    ],
    [
        2,
        {
            code: 2,
            message: 'Lamport balance below rent-exempt threshold"',
            name: 'NotRentExempt',
        },
    ],
    [
        3,
        {
            code: 3,
            message: 'Market authority is invalid"',
            name: 'InvalidMarketAuthority',
        },
    ],
    [
        4,
        {
            code: 4,
            message: 'Market owner is invalid"',
            name: 'InvalidMarketOwner',
        },
    ],
    [
        5,
        {
            code: 5,
            message: 'Input account owner is not the program address"',
            name: 'InvalidAccountOwner',
        },
    ],
    [
        6,
        {
            code: 6,
            message: 'Input token account is not owned by the correct token program id"',
            name: 'InvalidTokenOwner',
        },
    ],
    [
        7,
        {
            code: 7,
            message: 'Input token account is not valid"',
            name: 'InvalidTokenAccount',
        },
    ],
    [
        8,
        {
            code: 8,
            message: 'Input token mint account is not valid"',
            name: 'InvalidTokenMint',
        },
    ],
    [
        9,
        {
            code: 9,
            message: 'Input token program account is not valid"',
            name: 'InvalidTokenProgram',
        },
    ],
    [
        10,
        {
            code: 10,
            message: 'Input amount is invalid"',
            name: 'InvalidAmount',
        },
    ],
    [
        11,
        {
            code: 11,
            message: 'Input config value is invalid"',
            name: 'InvalidConfig',
        },
    ],
    [
        12,
        {
            code: 12,
            message: 'Input account must be a signer"',
            name: 'InvalidSigner',
        },
    ],
    [
        13,
        {
            code: 13,
            message: 'Invalid account input"',
            name: 'InvalidAccountInput',
        },
    ],
    [
        14,
        {
            code: 14,
            message: 'Math operation overflow"',
            name: 'MathOverflow',
        },
    ],
    [
        15,
        {
            code: 15,
            message: 'Token initialize mint failed"',
            name: 'TokenInitializeMintFailed',
        },
    ],
    [
        16,
        {
            code: 16,
            message: 'Token initialize account failed"',
            name: 'TokenInitializeAccountFailed',
        },
    ],
    [
        17,
        {
            code: 17,
            message: 'Token transfer failed"',
            name: 'TokenTransferFailed',
        },
    ],
    [
        18,
        {
            code: 18,
            message: 'Token mint to failed"',
            name: 'TokenMintToFailed',
        },
    ],
    [
        19,
        {
            code: 19,
            message: 'Token burn failed"',
            name: 'TokenBurnFailed',
        },
    ],
    [
        20,
        {
            code: 20,
            message: 'Insufficient liquidity available"',
            name: 'InsufficientLiquidity',
        },
    ],
    [
        21,
        {
            code: 21,
            message: 'Input reserve has collateral disabled"',
            name: 'ReserveCollateralDisabled',
        },
    ],
    [
        22,
        {
            code: 22,
            message: 'Reserve state needs to be refreshed"',
            name: 'ReserveStale',
        },
    ],
    [
        23,
        {
            code: 23,
            message: 'Withdraw amount too small"',
            name: 'WithdrawTooSmall',
        },
    ],
    [
        24,
        {
            code: 24,
            message: 'Withdraw amount too large"',
            name: 'WithdrawTooLarge',
        },
    ],
    [
        25,
        {
            code: 25,
            message: 'Borrow amount too small to receive liquidity after fees"',
            name: 'BorrowTooSmall',
        },
    ],
    [
        26,
        {
            code: 26,
            message: 'Borrow amount too large for deposited collateral"',
            name: 'BorrowTooLarge',
        },
    ],
    [
        27,
        {
            code: 27,
            message: 'Repay amount too small to transfer liquidity"',
            name: 'RepayTooSmall',
        },
    ],
    [
        28,
        {
            code: 28,
            message: 'Liquidation amount too small to receive collateral"',
            name: 'LiquidationTooSmall',
        },
    ],
    [
        29,
        {
            code: 29,
            message: 'Cannot liquidate healthy obligations"',
            name: 'ObligationHealthy',
        },
    ],
    [
        30,
        {
            code: 30,
            message: 'Obligation state needs to be refreshed"',
            name: 'ObligationStale',
        },
    ],
    [
        31,
        {
            code: 31,
            message: 'Obligation reserve limit exceeded"',
            name: 'ObligationReserveLimit',
        },
    ],
    [
        32,
        {
            code: 32,
            message: 'Obligation owner is invalid"',
            name: 'InvalidObligationOwner',
        },
    ],
    [
        33,
        {
            code: 33,
            message: 'Obligation deposits are empty"',
            name: 'ObligationDepositsEmpty',
        },
    ],
    [
        34,
        {
            code: 34,
            message: 'Obligation borrows are empty"',
            name: 'ObligationBorrowsEmpty',
        },
    ],
    [
        35,
        {
            code: 35,
            message: 'Obligation deposits have zero value"',
            name: 'ObligationDepositsZero',
        },
    ],
    [
        36,
        {
            code: 36,
            message: 'Obligation borrows have zero value"',
            name: 'ObligationBorrowsZero',
        },
    ],
    [
        37,
        {
            code: 37,
            message: 'Invalid obligation collateral"',
            name: 'InvalidObligationCollateral',
        },
    ],
    [
        38,
        {
            code: 38,
            message: 'Invalid obligation liquidity"',
            name: 'InvalidObligationLiquidity',
        },
    ],
    [
        39,
        {
            code: 39,
            message: 'Obligation collateral is empty"',
            name: 'ObligationCollateralEmpty',
        },
    ],
    [
        40,
        {
            code: 40,
            message: 'Obligation liquidity is empty"',
            name: 'ObligationLiquidityEmpty',
        },
    ],
    [
        41,
        {
            code: 41,
            message: 'Interest rate is negative"',
            name: 'NegativeInterestRate',
        },
    ],
    [
        42,
        {
            code: 42,
            message: 'Input oracle config is invalid"',
            name: 'InvalidOracleConfig',
        },
    ],
    [
        43,
        {
            code: 43,
            message: 'Input flash loan receiver program account is not valid"',
            name: 'InvalidFlashLoanReceiverProgram',
        },
    ],
    [
        44,
        {
            code: 44,
            message: 'Not enough liquidity after flash loan"',
            name: 'NotEnoughLiquidityAfterFlashLoan',
        },
    ],
]);
//# sourceMappingURL=token-lending.js.map