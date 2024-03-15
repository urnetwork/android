/// <reference types="node" />
import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import { Key } from '../types/Key';
export type UseAuthorityRecordArgs = {
    key: Key;
    allowedUses: beet.bignum;
    bump: number;
};
export declare class UseAuthorityRecord implements UseAuthorityRecordArgs {
    readonly key: Key;
    readonly allowedUses: beet.bignum;
    readonly bump: number;
    private constructor();
    static fromArgs(args: UseAuthorityRecordArgs): UseAuthorityRecord;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [UseAuthorityRecord, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey, commitmentOrConfig?: web3.Commitment | web3.GetAccountInfoConfig): Promise<UseAuthorityRecord>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        key: any;
        bump: any;
        allowedUses: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [UseAuthorityRecord, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        key: string;
        allowedUses: number | {
            toNumber: () => number;
        };
        bump: number;
    };
}
export declare const useAuthorityRecordBeet: beet.BeetStruct<UseAuthorityRecord, UseAuthorityRecordArgs>;
