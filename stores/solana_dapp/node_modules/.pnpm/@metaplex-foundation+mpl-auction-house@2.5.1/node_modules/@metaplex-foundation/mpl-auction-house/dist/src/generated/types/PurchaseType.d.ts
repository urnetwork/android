import * as beet from '@metaplex-foundation/beet';
export declare enum PurchaseType {
    ExecuteSale = 0,
    AuctioneerExecuteSale = 1
}
export declare const purchaseTypeBeet: beet.FixedSizeBeet<PurchaseType, PurchaseType>;
