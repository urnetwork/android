/// <reference types="node" />
import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import { Key } from '../types/Key';
export type EditionMarkerArgs = {
    key: Key;
    ledger: number[];
};
export declare class EditionMarker implements EditionMarkerArgs {
    readonly key: Key;
    readonly ledger: number[];
    private constructor();
    static fromArgs(args: EditionMarkerArgs): EditionMarker;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [EditionMarker, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey, commitmentOrConfig?: web3.Commitment | web3.GetAccountInfoConfig): Promise<EditionMarker>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        key: any;
        ledger: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [EditionMarker, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        key: string;
        ledger: number[];
    };
}
export declare const editionMarkerBeet: beet.BeetStruct<EditionMarker, EditionMarkerArgs>;
