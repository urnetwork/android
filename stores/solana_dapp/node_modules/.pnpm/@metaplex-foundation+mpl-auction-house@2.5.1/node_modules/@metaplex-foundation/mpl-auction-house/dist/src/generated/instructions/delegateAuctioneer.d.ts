import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { AuthorityScope } from '../types/AuthorityScope';
export declare type DelegateAuctioneerInstructionArgs = {
    scopes: AuthorityScope[];
};
export declare const delegateAuctioneerStruct: beet.FixableBeetArgsStruct<DelegateAuctioneerInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type DelegateAuctioneerInstructionAccounts = {
    auctionHouse: web3.PublicKey;
    authority: web3.PublicKey;
    auctioneerAuthority: web3.PublicKey;
    ahAuctioneerPda: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const delegateAuctioneerInstructionDiscriminator: number[];
export declare function createDelegateAuctioneerInstruction(accounts: DelegateAuctioneerInstructionAccounts, args: DelegateAuctioneerInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
