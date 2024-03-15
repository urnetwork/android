import * as beet from '@metaplex-foundation/beet';
import { UseMethod } from './UseMethod';
export type Uses = {
    useMethod: UseMethod;
    remaining: beet.bignum;
    total: beet.bignum;
};
export declare const usesBeet: beet.BeetArgsStruct<Uses>;
