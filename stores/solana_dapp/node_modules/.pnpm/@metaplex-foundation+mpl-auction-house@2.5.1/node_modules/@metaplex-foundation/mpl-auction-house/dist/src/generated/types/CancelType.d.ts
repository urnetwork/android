import * as beet from '@metaplex-foundation/beet';
export declare enum CancelType {
    Cancel = 0,
    AuctioneerCancel = 1
}
export declare const cancelTypeBeet: beet.FixedSizeBeet<CancelType, CancelType>;
