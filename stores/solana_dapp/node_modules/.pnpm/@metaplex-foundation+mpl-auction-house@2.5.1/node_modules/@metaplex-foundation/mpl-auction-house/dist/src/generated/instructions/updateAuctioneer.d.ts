import * as beet from '@metaplex-foundation/beet';
import * as web3 from '@solana/web3.js';
import { AuthorityScope } from '../types/AuthorityScope';
export declare type UpdateAuctioneerInstructionArgs = {
    scopes: AuthorityScope[];
};
export declare const updateAuctioneerStruct: beet.FixableBeetArgsStruct<UpdateAuctioneerInstructionArgs & {
    instructionDiscriminator: number[];
}>;
export declare type UpdateAuctioneerInstructionAccounts = {
    auctionHouse: web3.PublicKey;
    authority: web3.PublicKey;
    auctioneerAuthority: web3.PublicKey;
    ahAuctioneerPda: web3.PublicKey;
    systemProgram?: web3.PublicKey;
    anchorRemainingAccounts?: web3.AccountMeta[];
};
export declare const updateAuctioneerInstructionDiscriminator: number[];
export declare function createUpdateAuctioneerInstruction(accounts: UpdateAuctioneerInstructionAccounts, args: UpdateAuctioneerInstructionArgs, programId?: web3.PublicKey): web3.TransactionInstruction;
