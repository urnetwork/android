/// <reference types="node" />
import * as web3 from '@solana/web3.js';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import * as beet from '@metaplex-foundation/beet';
export declare type AuctioneerArgs = {
    auctioneerAuthority: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    bump: number;
};
export declare const auctioneerDiscriminator: number[];
export declare class Auctioneer implements AuctioneerArgs {
    readonly auctioneerAuthority: web3.PublicKey;
    readonly auctionHouse: web3.PublicKey;
    readonly bump: number;
    private constructor();
    static fromArgs(args: AuctioneerArgs): Auctioneer;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [Auctioneer, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey): Promise<Auctioneer>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        bump: any;
        accountDiscriminator: any;
        auctioneerAuthority: any;
        auctionHouse: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [Auctioneer, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        auctioneerAuthority: string;
        auctionHouse: string;
        bump: number;
    };
}
export declare const auctioneerBeet: beet.BeetStruct<Auctioneer, AuctioneerArgs & {
    accountDiscriminator: number[];
}>;
