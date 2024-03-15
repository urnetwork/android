import * as beet from '@metaplex-foundation/beet';
export declare enum BidType {
    PublicSale = 0,
    PrivateSale = 1,
    AuctioneerPublicSale = 2,
    AuctioneerPrivateSale = 3
}
export declare const bidTypeBeet: beet.FixedSizeBeet<BidType, BidType>;
