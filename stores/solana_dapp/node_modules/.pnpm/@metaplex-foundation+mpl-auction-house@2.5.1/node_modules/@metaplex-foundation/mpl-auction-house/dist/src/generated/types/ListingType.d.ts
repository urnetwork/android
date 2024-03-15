import * as beet from '@metaplex-foundation/beet';
export declare enum ListingType {
    Sell = 0,
    AuctioneerSell = 1
}
export declare const listingTypeBeet: beet.FixedSizeBeet<ListingType, ListingType>;
