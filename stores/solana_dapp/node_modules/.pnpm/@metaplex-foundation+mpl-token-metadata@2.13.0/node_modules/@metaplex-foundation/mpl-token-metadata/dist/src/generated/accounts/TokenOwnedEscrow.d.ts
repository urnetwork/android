/// <reference types="node" />
import * as web3 from '@solana/web3.js';
import * as beetSolana from '@metaplex-foundation/beet-solana';
import * as beet from '@metaplex-foundation/beet';
import { Key } from '../types/Key';
import { EscrowAuthority } from '../types/EscrowAuthority';
export type TokenOwnedEscrowArgs = {
    key: Key;
    baseToken: web3.PublicKey;
    authority: EscrowAuthority;
    bump: number;
};
export declare class TokenOwnedEscrow implements TokenOwnedEscrowArgs {
    readonly key: Key;
    readonly baseToken: web3.PublicKey;
    readonly authority: EscrowAuthority;
    readonly bump: number;
    private constructor();
    static fromArgs(args: TokenOwnedEscrowArgs): TokenOwnedEscrow;
    static fromAccountInfo(accountInfo: web3.AccountInfo<Buffer>, offset?: number): [TokenOwnedEscrow, number];
    static fromAccountAddress(connection: web3.Connection, address: web3.PublicKey, commitmentOrConfig?: web3.Commitment | web3.GetAccountInfoConfig): Promise<TokenOwnedEscrow>;
    static gpaBuilder(programId?: web3.PublicKey): beetSolana.GpaBuilder<TokenOwnedEscrowArgs>;
    static deserialize(buf: Buffer, offset?: number): [TokenOwnedEscrow, number];
    serialize(): [Buffer, number];
    static byteSize(args: TokenOwnedEscrowArgs): number;
    static getMinimumBalanceForRentExemption(args: TokenOwnedEscrowArgs, connection: web3.Connection, commitment?: web3.Commitment): Promise<number>;
    pretty(): {
        key: string;
        baseToken: string;
        authority: "Creator" | "TokenOwner";
        bump: number;
    };
}
export declare const tokenOwnedEscrowBeet: beet.FixableBeetStruct<TokenOwnedEscrow, TokenOwnedEscrowArgs>;
