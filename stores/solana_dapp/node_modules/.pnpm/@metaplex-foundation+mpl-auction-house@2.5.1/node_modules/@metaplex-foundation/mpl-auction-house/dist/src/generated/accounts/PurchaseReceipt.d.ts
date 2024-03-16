/// <reference types="node" />
import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
import * as beetSolana from '@metaplex-foundation/beet-solana';
export declare type PurchaseReceiptArgs = {
    bookkeeper: web3.PublicKey;
    buyer: web3.PublicKey;
    seller: web3.PublicKey;
    auctionHouse: web3.PublicKey;
    metadata: web3.PublicKey;
    tokenSize: beet.bignum;
    price: beet.bignum;
    bump: number;
    createdAt: beet.bignum;
};
export declare const purchaseReceiptDiscriminator: number[];
export declare class PurchaseReceipt implements PurchaseReceiptArgs {
    readonly bookkeeper: web3.PublicKey;
    readonly buyer: web3.PublicKey;
    readonly seller: web3.PublicKey;
    readonly auctionHouse: web3.PublicKey;
    readonly metadata: web3.PublicKey;
    readonly tokenSize: beet.bignum;
    readonly price: beet.bignum;
    readonly bump: number;
    readonly createdAt: beet.bignum;
    private constructor();
    static fromArgs(args: PurchaseReceiptArgs): PurchaseReceipt;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [PurchaseReceipt, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey): Promise<PurchaseReceipt>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<{
        bump: any;
        accountDiscriminator: any;
        auctionHouse: any;
        bookkeeper: any;
        buyer: any;
        metadata: any;
        price: any;
        tokenSize: any;
        createdAt: any;
        seller: any;
    }>;
    static deserialize(buf: Buffer, offset?: number): [PurchaseReceipt, number];
    serialize(): [Buffer, number];
    static get byteSize(): number;
    static getMinimumBalanceForRentExemption(connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    static hasCorrectByteSize(buf: Buffer, offset?: number): boolean;
    pretty(): {
        bookkeeper: string;
        buyer: string;
        seller: string;
        auctionHouse: string;
        metadata: string;
        tokenSize: number | {
            toNumber: () => number;
        };
        price: number | {
            toNumber: () => number;
        };
        bump: number;
        createdAt: number | {
            toNumber: () => number;
        };
    };
}
export declare const purchaseReceiptBeet: beet.BeetStruct<PurchaseReceipt, PurchaseReceiptArgs & {
    accountDiscriminator: number[];
}>;
