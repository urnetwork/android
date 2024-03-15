import * as beet from '@metaplex-foundation/beet';
export declare enum AuthorityScope {
    Deposit = 0,
    Buy = 1,
    PublicBuy = 2,
    ExecuteSale = 3,
    Sell = 4,
    Cancel = 5,
    Withdraw = 6
}
export declare const authorityScopeBeet: beet.FixedSizeBeet<AuthorityScope, AuthorityScope>;
