/// <reference types="node" />
import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import { Key } from '../types/Key';
export type EditionArgs = {
    key: Key;
    parent: web3.PublicKey;
    edition: beet.bignum;
};
export declare class Edition implements EditionArgs {
    readonly key: Key;
    readonly parent: web3.PublicKey;
    readonly edition: beet.bignum;
    private constructor();
    static fromArgs(args: EditionArgs): Edition;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [Edition, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey, commitmentOrConfig?: web3.Commitment | web3.GetAccountInfoConfig): Promise<Edition>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        key: any;
        parent: any;
        edition: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [Edition, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        key: string;
        parent: string;
        edition: number | {
            toNumber: () => number;
        };
    };
}
export declare const editionBeet: beet.BeetStruct<Edition, EditionArgs>;
