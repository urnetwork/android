/// <reference types="node" />
import * as web3 from '@solana/web3.js';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import * as beet from '@metaplex-foundation/beet';
export declare type CandyGuardArgs = {
    base: web3.PublicKey;
    bump: number;
    authority: web3.PublicKey;
};
export declare const candyGuardDiscriminator: number[];
export declare class CandyGuard implements CandyGuardArgs {
    readonly base: web3.PublicKey;
    readonly bump: number;
    readonly authority: web3.PublicKey;
    private constructor();
    static fromArgs(args: CandyGuardArgs): CandyGuard;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [CandyGuard, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey): Promise<CandyGuard>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        base: any;
        bump: any;
        authority: any;
        accountDiscriminator: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [CandyGuard, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        base: string;
        bump: number;
        authority: string;
    };
}
export declare const candyGuardBeet: beet.BeetStruct<CandyGuard, CandyGuardArgs & {
    accountDiscriminator: number[];
}>;
