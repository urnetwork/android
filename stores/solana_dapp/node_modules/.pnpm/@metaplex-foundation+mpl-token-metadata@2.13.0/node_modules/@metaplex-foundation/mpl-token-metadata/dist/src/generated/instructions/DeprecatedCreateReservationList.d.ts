import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
export declare const DeprecatedCreateReservationListStruct: beet.BeetArgsStruct<{
    instructionDiscriminator: number;
}>;
export type DeprecatedCreateReservationListInstructionAccounts = {
    reservationList: web3.PublicKey;
    payer: web3.PublicKey;
    updateAuthority: web3.PublicKey;
    masterEdition: web3.PublicKey;
    resource: web3.PublicKey;
    metadata: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    rent?: web3.PublicKey;
};
export declare const deprecatedCreateReservationListInstructionDiscriminator = 6;
export declare function createDeprecatedCreateReservationListInstruction(accounts: DeprecatedCreateReservationListInstructionAccounts, programId?: web3.PublicKey): web3.TransactionInstruction;
