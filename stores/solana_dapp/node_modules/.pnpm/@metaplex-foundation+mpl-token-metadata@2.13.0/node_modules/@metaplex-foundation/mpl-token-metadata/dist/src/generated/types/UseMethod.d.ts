import * as beet from '@metaplex-foundation/beet';
export declare enum UseMethod {
    Burn = 0,
    Multiple = 1,
    Single = 2
}
export declare const useMethodBeet: beet.FixedSizeBeet<UseMethod, UseMethod>;
