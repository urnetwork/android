import * as web3 from '@solana/web3.js';
import * as beet from '@metaplex-foundation/beet';
export type ReservationV1 = {
    address: web3.PublicKey;
    spotsRemaining: number;
    totalSpots: number;
};
export declare const reservationV1Beet: beet.BeetArgsStruct<ReservationV1>;
