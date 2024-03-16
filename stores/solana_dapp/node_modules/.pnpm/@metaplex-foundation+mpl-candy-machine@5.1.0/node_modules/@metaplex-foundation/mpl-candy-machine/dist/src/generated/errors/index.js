"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.NoUnlockWithNFTsStillFrozenError = exports.InvalidFreezeWithdrawTokenAddressError = exports.MissingRemoveFreezeTokenAccountsError = exports.NoWithdrawWithFrozenFundsError = exports.NoWithdrawWithFreezeError = exports.EnteredFreezeIsMoreThanMaxFreezeError = exports.FreezePDAMismatchError = exports.IncorrectFreezeAtaError = exports.MissingFreezeAtaError = exports.IncorrectRemainingAccountsLenError = exports.InvalidThawNftError = exports.NoChangingTokenWithFreezeError = exports.NoChangingAuthorityWithCollectionError = exports.NoChangingFreezeDuringMintError = exports.GatewayProgramErrorError = exports.CandyCollectionRequiresRetainAuthorityError = exports.NoChangingCollectionDuringMintError = exports.MissingSetCollectionDuringMintError = exports.MetadataAccountMustBeEmptyError = exports.SlotHashesEmptyError = exports.MismatchedCollectionMintError = exports.MismatchedCollectionPDAError = exports.IncorrectCollectionAuthorityError = exports.IncorrectSlotHashesPubkeyError = exports.CannotSwitchToHiddenSettingsError = exports.SuspiciousTransactionError = exports.InvalidStringError = exports.CannotFindUsableConfigLineError = exports.NetworkExpireFeatureMissingError = exports.GatewayTokenExpireTimeInvalidError = exports.GatewayTokenMissingError = exports.GatewayAppMissingError = exports.TokenBurnFailedError = exports.NoWhitelistTokenError = exports.PublicKeyMismatchError = exports.DerivedKeyInvalidError = exports.CannotChangeNumberOfLinesError = exports.HiddenSettingsConfigsDoNotHaveConfigLinesError = exports.CandyMachineNotLiveError = exports.CandyMachineEmptyError = exports.TokenTransferFailedError = exports.NotEnoughSOLError = exports.NotEnoughTokensError = exports.UuidMustBeExactly6LengthError = exports.TooManyCreatorsError = exports.NumericalOverflowErrorError = exports.IndexGreaterThanLengthError = exports.MintMismatchError = exports.UninitializedError = exports.IncorrectOwnerError = void 0;
exports.errorFromName = exports.errorFromCode = exports.CannotSwitchFromHiddenSettingsError = exports.SizedCollectionMetadataMustBeMutableError = void 0;
const createErrorFromCodeLookup = new Map();
const createErrorFromNameLookup = new Map();
class IncorrectOwnerError extends Error {
    constructor() {
        super('Account does not have correct owner!');
        this.code = 0x1770;
        this.name = 'IncorrectOwner';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IncorrectOwnerError);
        }
    }
}
exports.IncorrectOwnerError = IncorrectOwnerError;
createErrorFromCodeLookup.set(0x1770, () => new IncorrectOwnerError());
createErrorFromNameLookup.set('IncorrectOwner', () => new IncorrectOwnerError());
class UninitializedError extends Error {
    constructor() {
        super('Account is not initialized!');
        this.code = 0x1771;
        this.name = 'Uninitialized';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, UninitializedError);
        }
    }
}
exports.UninitializedError = UninitializedError;
createErrorFromCodeLookup.set(0x1771, () => new UninitializedError());
createErrorFromNameLookup.set('Uninitialized', () => new UninitializedError());
class MintMismatchError extends Error {
    constructor() {
        super('Mint Mismatch!');
        this.code = 0x1772;
        this.name = 'MintMismatch';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MintMismatchError);
        }
    }
}
exports.MintMismatchError = MintMismatchError;
createErrorFromCodeLookup.set(0x1772, () => new MintMismatchError());
createErrorFromNameLookup.set('MintMismatch', () => new MintMismatchError());
class IndexGreaterThanLengthError extends Error {
    constructor() {
        super('Index greater than length!');
        this.code = 0x1773;
        this.name = 'IndexGreaterThanLength';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IndexGreaterThanLengthError);
        }
    }
}
exports.IndexGreaterThanLengthError = IndexGreaterThanLengthError;
createErrorFromCodeLookup.set(0x1773, () => new IndexGreaterThanLengthError());
createErrorFromNameLookup.set('IndexGreaterThanLength', () => new IndexGreaterThanLengthError());
class NumericalOverflowErrorError extends Error {
    constructor() {
        super('Numerical overflow error!');
        this.code = 0x1774;
        this.name = 'NumericalOverflowError';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NumericalOverflowErrorError);
        }
    }
}
exports.NumericalOverflowErrorError = NumericalOverflowErrorError;
createErrorFromCodeLookup.set(0x1774, () => new NumericalOverflowErrorError());
createErrorFromNameLookup.set('NumericalOverflowError', () => new NumericalOverflowErrorError());
class TooManyCreatorsError extends Error {
    constructor() {
        super('Can only provide up to 4 creators to candy machine (because candy machine is one)!');
        this.code = 0x1775;
        this.name = 'TooManyCreators';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, TooManyCreatorsError);
        }
    }
}
exports.TooManyCreatorsError = TooManyCreatorsError;
createErrorFromCodeLookup.set(0x1775, () => new TooManyCreatorsError());
createErrorFromNameLookup.set('TooManyCreators', () => new TooManyCreatorsError());
class UuidMustBeExactly6LengthError extends Error {
    constructor() {
        super('Uuid must be exactly of 6 length');
        this.code = 0x1776;
        this.name = 'UuidMustBeExactly6Length';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, UuidMustBeExactly6LengthError);
        }
    }
}
exports.UuidMustBeExactly6LengthError = UuidMustBeExactly6LengthError;
createErrorFromCodeLookup.set(0x1776, () => new UuidMustBeExactly6LengthError());
createErrorFromNameLookup.set('UuidMustBeExactly6Length', () => new UuidMustBeExactly6LengthError());
class NotEnoughTokensError extends Error {
    constructor() {
        super('Not enough tokens to pay for this minting');
        this.code = 0x1777;
        this.name = 'NotEnoughTokens';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NotEnoughTokensError);
        }
    }
}
exports.NotEnoughTokensError = NotEnoughTokensError;
createErrorFromCodeLookup.set(0x1777, () => new NotEnoughTokensError());
createErrorFromNameLookup.set('NotEnoughTokens', () => new NotEnoughTokensError());
class NotEnoughSOLError extends Error {
    constructor() {
        super('Not enough SOL to pay for this minting');
        this.code = 0x1778;
        this.name = 'NotEnoughSOL';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NotEnoughSOLError);
        }
    }
}
exports.NotEnoughSOLError = NotEnoughSOLError;
createErrorFromCodeLookup.set(0x1778, () => new NotEnoughSOLError());
createErrorFromNameLookup.set('NotEnoughSOL', () => new NotEnoughSOLError());
class TokenTransferFailedError extends Error {
    constructor() {
        super('Token transfer failed');
        this.code = 0x1779;
        this.name = 'TokenTransferFailed';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, TokenTransferFailedError);
        }
    }
}
exports.TokenTransferFailedError = TokenTransferFailedError;
createErrorFromCodeLookup.set(0x1779, () => new TokenTransferFailedError());
createErrorFromNameLookup.set('TokenTransferFailed', () => new TokenTransferFailedError());
class CandyMachineEmptyError extends Error {
    constructor() {
        super('Candy machine is empty!');
        this.code = 0x177a;
        this.name = 'CandyMachineEmpty';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CandyMachineEmptyError);
        }
    }
}
exports.CandyMachineEmptyError = CandyMachineEmptyError;
createErrorFromCodeLookup.set(0x177a, () => new CandyMachineEmptyError());
createErrorFromNameLookup.set('CandyMachineEmpty', () => new CandyMachineEmptyError());
class CandyMachineNotLiveError extends Error {
    constructor() {
        super('Candy machine is not live!');
        this.code = 0x177b;
        this.name = 'CandyMachineNotLive';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CandyMachineNotLiveError);
        }
    }
}
exports.CandyMachineNotLiveError = CandyMachineNotLiveError;
createErrorFromCodeLookup.set(0x177b, () => new CandyMachineNotLiveError());
createErrorFromNameLookup.set('CandyMachineNotLive', () => new CandyMachineNotLiveError());
class HiddenSettingsConfigsDoNotHaveConfigLinesError extends Error {
    constructor() {
        super('Configs that are using hidden uris do not have config lines, they have a single hash representing hashed order');
        this.code = 0x177c;
        this.name = 'HiddenSettingsConfigsDoNotHaveConfigLines';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, HiddenSettingsConfigsDoNotHaveConfigLinesError);
        }
    }
}
exports.HiddenSettingsConfigsDoNotHaveConfigLinesError = HiddenSettingsConfigsDoNotHaveConfigLinesError;
createErrorFromCodeLookup.set(0x177c, () => new HiddenSettingsConfigsDoNotHaveConfigLinesError());
createErrorFromNameLookup.set('HiddenSettingsConfigsDoNotHaveConfigLines', () => new HiddenSettingsConfigsDoNotHaveConfigLinesError());
class CannotChangeNumberOfLinesError extends Error {
    constructor() {
        super('Cannot change number of lines unless is a hidden config');
        this.code = 0x177d;
        this.name = 'CannotChangeNumberOfLines';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CannotChangeNumberOfLinesError);
        }
    }
}
exports.CannotChangeNumberOfLinesError = CannotChangeNumberOfLinesError;
createErrorFromCodeLookup.set(0x177d, () => new CannotChangeNumberOfLinesError());
createErrorFromNameLookup.set('CannotChangeNumberOfLines', () => new CannotChangeNumberOfLinesError());
class DerivedKeyInvalidError extends Error {
    constructor() {
        super('Derived key invalid');
        this.code = 0x177e;
        this.name = 'DerivedKeyInvalid';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, DerivedKeyInvalidError);
        }
    }
}
exports.DerivedKeyInvalidError = DerivedKeyInvalidError;
createErrorFromCodeLookup.set(0x177e, () => new DerivedKeyInvalidError());
createErrorFromNameLookup.set('DerivedKeyInvalid', () => new DerivedKeyInvalidError());
class PublicKeyMismatchError extends Error {
    constructor() {
        super('Public key mismatch');
        this.code = 0x177f;
        this.name = 'PublicKeyMismatch';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, PublicKeyMismatchError);
        }
    }
}
exports.PublicKeyMismatchError = PublicKeyMismatchError;
createErrorFromCodeLookup.set(0x177f, () => new PublicKeyMismatchError());
createErrorFromNameLookup.set('PublicKeyMismatch', () => new PublicKeyMismatchError());
class NoWhitelistTokenError extends Error {
    constructor() {
        super('No whitelist token present');
        this.code = 0x1780;
        this.name = 'NoWhitelistToken';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoWhitelistTokenError);
        }
    }
}
exports.NoWhitelistTokenError = NoWhitelistTokenError;
createErrorFromCodeLookup.set(0x1780, () => new NoWhitelistTokenError());
createErrorFromNameLookup.set('NoWhitelistToken', () => new NoWhitelistTokenError());
class TokenBurnFailedError extends Error {
    constructor() {
        super('Token burn failed');
        this.code = 0x1781;
        this.name = 'TokenBurnFailed';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, TokenBurnFailedError);
        }
    }
}
exports.TokenBurnFailedError = TokenBurnFailedError;
createErrorFromCodeLookup.set(0x1781, () => new TokenBurnFailedError());
createErrorFromNameLookup.set('TokenBurnFailed', () => new TokenBurnFailedError());
class GatewayAppMissingError extends Error {
    constructor() {
        super('Missing gateway app when required');
        this.code = 0x1782;
        this.name = 'GatewayAppMissing';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, GatewayAppMissingError);
        }
    }
}
exports.GatewayAppMissingError = GatewayAppMissingError;
createErrorFromCodeLookup.set(0x1782, () => new GatewayAppMissingError());
createErrorFromNameLookup.set('GatewayAppMissing', () => new GatewayAppMissingError());
class GatewayTokenMissingError extends Error {
    constructor() {
        super('Missing gateway token when required');
        this.code = 0x1783;
        this.name = 'GatewayTokenMissing';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, GatewayTokenMissingError);
        }
    }
}
exports.GatewayTokenMissingError = GatewayTokenMissingError;
createErrorFromCodeLookup.set(0x1783, () => new GatewayTokenMissingError());
createErrorFromNameLookup.set('GatewayTokenMissing', () => new GatewayTokenMissingError());
class GatewayTokenExpireTimeInvalidError extends Error {
    constructor() {
        super('Invalid gateway token expire time');
        this.code = 0x1784;
        this.name = 'GatewayTokenExpireTimeInvalid';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, GatewayTokenExpireTimeInvalidError);
        }
    }
}
exports.GatewayTokenExpireTimeInvalidError = GatewayTokenExpireTimeInvalidError;
createErrorFromCodeLookup.set(0x1784, () => new GatewayTokenExpireTimeInvalidError());
createErrorFromNameLookup.set('GatewayTokenExpireTimeInvalid', () => new GatewayTokenExpireTimeInvalidError());
class NetworkExpireFeatureMissingError extends Error {
    constructor() {
        super('Missing gateway network expire feature when required');
        this.code = 0x1785;
        this.name = 'NetworkExpireFeatureMissing';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NetworkExpireFeatureMissingError);
        }
    }
}
exports.NetworkExpireFeatureMissingError = NetworkExpireFeatureMissingError;
createErrorFromCodeLookup.set(0x1785, () => new NetworkExpireFeatureMissingError());
createErrorFromNameLookup.set('NetworkExpireFeatureMissing', () => new NetworkExpireFeatureMissingError());
class CannotFindUsableConfigLineError extends Error {
    constructor() {
        super('Unable to find an unused config line near your random number index');
        this.code = 0x1786;
        this.name = 'CannotFindUsableConfigLine';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CannotFindUsableConfigLineError);
        }
    }
}
exports.CannotFindUsableConfigLineError = CannotFindUsableConfigLineError;
createErrorFromCodeLookup.set(0x1786, () => new CannotFindUsableConfigLineError());
createErrorFromNameLookup.set('CannotFindUsableConfigLine', () => new CannotFindUsableConfigLineError());
class InvalidStringError extends Error {
    constructor() {
        super('Invalid string');
        this.code = 0x1787;
        this.name = 'InvalidString';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, InvalidStringError);
        }
    }
}
exports.InvalidStringError = InvalidStringError;
createErrorFromCodeLookup.set(0x1787, () => new InvalidStringError());
createErrorFromNameLookup.set('InvalidString', () => new InvalidStringError());
class SuspiciousTransactionError extends Error {
    constructor() {
        super('Suspicious transaction detected');
        this.code = 0x1788;
        this.name = 'SuspiciousTransaction';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, SuspiciousTransactionError);
        }
    }
}
exports.SuspiciousTransactionError = SuspiciousTransactionError;
createErrorFromCodeLookup.set(0x1788, () => new SuspiciousTransactionError());
createErrorFromNameLookup.set('SuspiciousTransaction', () => new SuspiciousTransactionError());
class CannotSwitchToHiddenSettingsError extends Error {
    constructor() {
        super('Cannot Switch to Hidden Settings after items available is greater than 0');
        this.code = 0x1789;
        this.name = 'CannotSwitchToHiddenSettings';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CannotSwitchToHiddenSettingsError);
        }
    }
}
exports.CannotSwitchToHiddenSettingsError = CannotSwitchToHiddenSettingsError;
createErrorFromCodeLookup.set(0x1789, () => new CannotSwitchToHiddenSettingsError());
createErrorFromNameLookup.set('CannotSwitchToHiddenSettings', () => new CannotSwitchToHiddenSettingsError());
class IncorrectSlotHashesPubkeyError extends Error {
    constructor() {
        super('Incorrect SlotHashes PubKey');
        this.code = 0x178a;
        this.name = 'IncorrectSlotHashesPubkey';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IncorrectSlotHashesPubkeyError);
        }
    }
}
exports.IncorrectSlotHashesPubkeyError = IncorrectSlotHashesPubkeyError;
createErrorFromCodeLookup.set(0x178a, () => new IncorrectSlotHashesPubkeyError());
createErrorFromNameLookup.set('IncorrectSlotHashesPubkey', () => new IncorrectSlotHashesPubkeyError());
class IncorrectCollectionAuthorityError extends Error {
    constructor() {
        super('Incorrect collection NFT authority');
        this.code = 0x178b;
        this.name = 'IncorrectCollectionAuthority';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IncorrectCollectionAuthorityError);
        }
    }
}
exports.IncorrectCollectionAuthorityError = IncorrectCollectionAuthorityError;
createErrorFromCodeLookup.set(0x178b, () => new IncorrectCollectionAuthorityError());
createErrorFromNameLookup.set('IncorrectCollectionAuthority', () => new IncorrectCollectionAuthorityError());
class MismatchedCollectionPDAError extends Error {
    constructor() {
        super('Collection PDA address is invalid');
        this.code = 0x178c;
        this.name = 'MismatchedCollectionPDA';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MismatchedCollectionPDAError);
        }
    }
}
exports.MismatchedCollectionPDAError = MismatchedCollectionPDAError;
createErrorFromCodeLookup.set(0x178c, () => new MismatchedCollectionPDAError());
createErrorFromNameLookup.set('MismatchedCollectionPDA', () => new MismatchedCollectionPDAError());
class MismatchedCollectionMintError extends Error {
    constructor() {
        super("Provided mint account doesn't match collection PDA mint");
        this.code = 0x178d;
        this.name = 'MismatchedCollectionMint';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MismatchedCollectionMintError);
        }
    }
}
exports.MismatchedCollectionMintError = MismatchedCollectionMintError;
createErrorFromCodeLookup.set(0x178d, () => new MismatchedCollectionMintError());
createErrorFromNameLookup.set('MismatchedCollectionMint', () => new MismatchedCollectionMintError());
class SlotHashesEmptyError extends Error {
    constructor() {
        super('Slot hashes Sysvar is empty');
        this.code = 0x178e;
        this.name = 'SlotHashesEmpty';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, SlotHashesEmptyError);
        }
    }
}
exports.SlotHashesEmptyError = SlotHashesEmptyError;
createErrorFromCodeLookup.set(0x178e, () => new SlotHashesEmptyError());
createErrorFromNameLookup.set('SlotHashesEmpty', () => new SlotHashesEmptyError());
class MetadataAccountMustBeEmptyError extends Error {
    constructor() {
        super('The metadata account has data in it, and this must be empty to mint a new NFT');
        this.code = 0x178f;
        this.name = 'MetadataAccountMustBeEmpty';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MetadataAccountMustBeEmptyError);
        }
    }
}
exports.MetadataAccountMustBeEmptyError = MetadataAccountMustBeEmptyError;
createErrorFromCodeLookup.set(0x178f, () => new MetadataAccountMustBeEmptyError());
createErrorFromNameLookup.set('MetadataAccountMustBeEmpty', () => new MetadataAccountMustBeEmptyError());
class MissingSetCollectionDuringMintError extends Error {
    constructor() {
        super('Missing set collection during mint IX for Candy Machine with collection set');
        this.code = 0x1790;
        this.name = 'MissingSetCollectionDuringMint';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MissingSetCollectionDuringMintError);
        }
    }
}
exports.MissingSetCollectionDuringMintError = MissingSetCollectionDuringMintError;
createErrorFromCodeLookup.set(0x1790, () => new MissingSetCollectionDuringMintError());
createErrorFromNameLookup.set('MissingSetCollectionDuringMint', () => new MissingSetCollectionDuringMintError());
class NoChangingCollectionDuringMintError extends Error {
    constructor() {
        super("Can't change collection settings after items have begun to be minted");
        this.code = 0x1791;
        this.name = 'NoChangingCollectionDuringMint';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoChangingCollectionDuringMintError);
        }
    }
}
exports.NoChangingCollectionDuringMintError = NoChangingCollectionDuringMintError;
createErrorFromCodeLookup.set(0x1791, () => new NoChangingCollectionDuringMintError());
createErrorFromNameLookup.set('NoChangingCollectionDuringMint', () => new NoChangingCollectionDuringMintError());
class CandyCollectionRequiresRetainAuthorityError extends Error {
    constructor() {
        super('Retain authority must be true for Candy Machines with a collection set');
        this.code = 0x1792;
        this.name = 'CandyCollectionRequiresRetainAuthority';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CandyCollectionRequiresRetainAuthorityError);
        }
    }
}
exports.CandyCollectionRequiresRetainAuthorityError = CandyCollectionRequiresRetainAuthorityError;
createErrorFromCodeLookup.set(0x1792, () => new CandyCollectionRequiresRetainAuthorityError());
createErrorFromNameLookup.set('CandyCollectionRequiresRetainAuthority', () => new CandyCollectionRequiresRetainAuthorityError());
class GatewayProgramErrorError extends Error {
    constructor() {
        super('Error within Gateway program');
        this.code = 0x1793;
        this.name = 'GatewayProgramError';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, GatewayProgramErrorError);
        }
    }
}
exports.GatewayProgramErrorError = GatewayProgramErrorError;
createErrorFromCodeLookup.set(0x1793, () => new GatewayProgramErrorError());
createErrorFromNameLookup.set('GatewayProgramError', () => new GatewayProgramErrorError());
class NoChangingFreezeDuringMintError extends Error {
    constructor() {
        super("Can't change freeze settings after items have begun to be minted. You can only disable.");
        this.code = 0x1794;
        this.name = 'NoChangingFreezeDuringMint';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoChangingFreezeDuringMintError);
        }
    }
}
exports.NoChangingFreezeDuringMintError = NoChangingFreezeDuringMintError;
createErrorFromCodeLookup.set(0x1794, () => new NoChangingFreezeDuringMintError());
createErrorFromNameLookup.set('NoChangingFreezeDuringMint', () => new NoChangingFreezeDuringMintError());
class NoChangingAuthorityWithCollectionError extends Error {
    constructor() {
        super("Can't change authority while collection is enabled. Disable collection first.");
        this.code = 0x1795;
        this.name = 'NoChangingAuthorityWithCollection';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoChangingAuthorityWithCollectionError);
        }
    }
}
exports.NoChangingAuthorityWithCollectionError = NoChangingAuthorityWithCollectionError;
createErrorFromCodeLookup.set(0x1795, () => new NoChangingAuthorityWithCollectionError());
createErrorFromNameLookup.set('NoChangingAuthorityWithCollection', () => new NoChangingAuthorityWithCollectionError());
class NoChangingTokenWithFreezeError extends Error {
    constructor() {
        super("Can't change token while freeze is enabled. Disable freeze first.");
        this.code = 0x1796;
        this.name = 'NoChangingTokenWithFreeze';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoChangingTokenWithFreezeError);
        }
    }
}
exports.NoChangingTokenWithFreezeError = NoChangingTokenWithFreezeError;
createErrorFromCodeLookup.set(0x1796, () => new NoChangingTokenWithFreezeError());
createErrorFromNameLookup.set('NoChangingTokenWithFreeze', () => new NoChangingTokenWithFreezeError());
class InvalidThawNftError extends Error {
    constructor() {
        super('Cannot thaw NFT unless all NFTs are minted or Candy Machine authority enables thawing');
        this.code = 0x1797;
        this.name = 'InvalidThawNft';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, InvalidThawNftError);
        }
    }
}
exports.InvalidThawNftError = InvalidThawNftError;
createErrorFromCodeLookup.set(0x1797, () => new InvalidThawNftError());
createErrorFromNameLookup.set('InvalidThawNft', () => new InvalidThawNftError());
class IncorrectRemainingAccountsLenError extends Error {
    constructor() {
        super("The number of remaining accounts passed in doesn't match the Candy Machine settings");
        this.code = 0x1798;
        this.name = 'IncorrectRemainingAccountsLen';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IncorrectRemainingAccountsLenError);
        }
    }
}
exports.IncorrectRemainingAccountsLenError = IncorrectRemainingAccountsLenError;
createErrorFromCodeLookup.set(0x1798, () => new IncorrectRemainingAccountsLenError());
createErrorFromNameLookup.set('IncorrectRemainingAccountsLen', () => new IncorrectRemainingAccountsLenError());
class MissingFreezeAtaError extends Error {
    constructor() {
        super('FreezePDA ATA needs to be passed in if token mint is enabled.');
        this.code = 0x1799;
        this.name = 'MissingFreezeAta';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MissingFreezeAtaError);
        }
    }
}
exports.MissingFreezeAtaError = MissingFreezeAtaError;
createErrorFromCodeLookup.set(0x1799, () => new MissingFreezeAtaError());
createErrorFromNameLookup.set('MissingFreezeAta', () => new MissingFreezeAtaError());
class IncorrectFreezeAtaError extends Error {
    constructor() {
        super('Incorrect freeze ATA address.');
        this.code = 0x179a;
        this.name = 'IncorrectFreezeAta';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, IncorrectFreezeAtaError);
        }
    }
}
exports.IncorrectFreezeAtaError = IncorrectFreezeAtaError;
createErrorFromCodeLookup.set(0x179a, () => new IncorrectFreezeAtaError());
createErrorFromNameLookup.set('IncorrectFreezeAta', () => new IncorrectFreezeAtaError());
class FreezePDAMismatchError extends Error {
    constructor() {
        super("FreezePDA doesn't belong to this Candy Machine.");
        this.code = 0x179b;
        this.name = 'FreezePDAMismatch';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, FreezePDAMismatchError);
        }
    }
}
exports.FreezePDAMismatchError = FreezePDAMismatchError;
createErrorFromCodeLookup.set(0x179b, () => new FreezePDAMismatchError());
createErrorFromNameLookup.set('FreezePDAMismatch', () => new FreezePDAMismatchError());
class EnteredFreezeIsMoreThanMaxFreezeError extends Error {
    constructor() {
        super("Freeze time can't be longer than MAX_FREEZE_TIME.");
        this.code = 0x179c;
        this.name = 'EnteredFreezeIsMoreThanMaxFreeze';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, EnteredFreezeIsMoreThanMaxFreezeError);
        }
    }
}
exports.EnteredFreezeIsMoreThanMaxFreezeError = EnteredFreezeIsMoreThanMaxFreezeError;
createErrorFromCodeLookup.set(0x179c, () => new EnteredFreezeIsMoreThanMaxFreezeError());
createErrorFromNameLookup.set('EnteredFreezeIsMoreThanMaxFreeze', () => new EnteredFreezeIsMoreThanMaxFreezeError());
class NoWithdrawWithFreezeError extends Error {
    constructor() {
        super("Can't withdraw Candy Machine while freeze is active. Disable freeze first.");
        this.code = 0x179d;
        this.name = 'NoWithdrawWithFreeze';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoWithdrawWithFreezeError);
        }
    }
}
exports.NoWithdrawWithFreezeError = NoWithdrawWithFreezeError;
createErrorFromCodeLookup.set(0x179d, () => new NoWithdrawWithFreezeError());
createErrorFromNameLookup.set('NoWithdrawWithFreeze', () => new NoWithdrawWithFreezeError());
class NoWithdrawWithFrozenFundsError extends Error {
    constructor() {
        super("Can't withdraw Candy Machine while frozen funds need to be redeemed. Unlock funds first.");
        this.code = 0x179e;
        this.name = 'NoWithdrawWithFrozenFunds';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoWithdrawWithFrozenFundsError);
        }
    }
}
exports.NoWithdrawWithFrozenFundsError = NoWithdrawWithFrozenFundsError;
createErrorFromCodeLookup.set(0x179e, () => new NoWithdrawWithFrozenFundsError());
createErrorFromNameLookup.set('NoWithdrawWithFrozenFunds', () => new NoWithdrawWithFrozenFundsError());
class MissingRemoveFreezeTokenAccountsError extends Error {
    constructor() {
        super('Missing required remaining accounts for remove_freeze with token mint.');
        this.code = 0x179f;
        this.name = 'MissingRemoveFreezeTokenAccounts';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, MissingRemoveFreezeTokenAccountsError);
        }
    }
}
exports.MissingRemoveFreezeTokenAccountsError = MissingRemoveFreezeTokenAccountsError;
createErrorFromCodeLookup.set(0x179f, () => new MissingRemoveFreezeTokenAccountsError());
createErrorFromNameLookup.set('MissingRemoveFreezeTokenAccounts', () => new MissingRemoveFreezeTokenAccountsError());
class InvalidFreezeWithdrawTokenAddressError extends Error {
    constructor() {
        super("Can't withdraw SPL Token from freeze PDA into itself");
        this.code = 0x17a0;
        this.name = 'InvalidFreezeWithdrawTokenAddress';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, InvalidFreezeWithdrawTokenAddressError);
        }
    }
}
exports.InvalidFreezeWithdrawTokenAddressError = InvalidFreezeWithdrawTokenAddressError;
createErrorFromCodeLookup.set(0x17a0, () => new InvalidFreezeWithdrawTokenAddressError());
createErrorFromNameLookup.set('InvalidFreezeWithdrawTokenAddress', () => new InvalidFreezeWithdrawTokenAddressError());
class NoUnlockWithNFTsStillFrozenError extends Error {
    constructor() {
        super("Can't unlock funds while NFTs are still frozen. Run thaw on all NFTs first.");
        this.code = 0x17a1;
        this.name = 'NoUnlockWithNFTsStillFrozen';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, NoUnlockWithNFTsStillFrozenError);
        }
    }
}
exports.NoUnlockWithNFTsStillFrozenError = NoUnlockWithNFTsStillFrozenError;
createErrorFromCodeLookup.set(0x17a1, () => new NoUnlockWithNFTsStillFrozenError());
createErrorFromNameLookup.set('NoUnlockWithNFTsStillFrozen', () => new NoUnlockWithNFTsStillFrozenError());
class SizedCollectionMetadataMustBeMutableError extends Error {
    constructor() {
        super('Setting a sized collection requires the collection metadata to be mutable.');
        this.code = 0x17a2;
        this.name = 'SizedCollectionMetadataMustBeMutable';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, SizedCollectionMetadataMustBeMutableError);
        }
    }
}
exports.SizedCollectionMetadataMustBeMutableError = SizedCollectionMetadataMustBeMutableError;
createErrorFromCodeLookup.set(0x17a2, () => new SizedCollectionMetadataMustBeMutableError());
createErrorFromNameLookup.set('SizedCollectionMetadataMustBeMutable', () => new SizedCollectionMetadataMustBeMutableError());
class CannotSwitchFromHiddenSettingsError extends Error {
    constructor() {
        super('Cannot remove Hidden Settings.');
        this.code = 0x17a3;
        this.name = 'CannotSwitchFromHiddenSettings';
        if (typeof Error.captureStackTrace === 'function') {
            Error.captureStackTrace(this, CannotSwitchFromHiddenSettingsError);
        }
    }
}
exports.CannotSwitchFromHiddenSettingsError = CannotSwitchFromHiddenSettingsError;
createErrorFromCodeLookup.set(0x17a3, () => new CannotSwitchFromHiddenSettingsError());
createErrorFromNameLookup.set('CannotSwitchFromHiddenSettings', () => new CannotSwitchFromHiddenSettingsError());
function errorFromCode(code) {
    const createError = createErrorFromCodeLookup.get(code);
    return createError != null ? createError() : null;
}
exports.errorFromCode = errorFromCode;
function errorFromName(name) {
    const createError = createErrorFromNameLookup.get(name);
    return createError != null ? createError() : null;
}
exports.errorFromName = errorFromName;
//# sourceMappingURL=index.js.map