import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
export type Reservation = {
    address: web3.PublicKey;
    spotsRemaining: beet.bignum;
    totalSpots: beet.bignum;
};
export declare const reservationBeet: beet.BeetArgsStruct<Reservation>;
