import * as beet from '@metaplex-foundation/beet';
import { GuardSet } from './GuardSet';
import { Group } from './Group';
export declare type CandyGuardData = {
    default: GuardSet;
    groups: beet.COption<Group[]>;
};
export declare const candyGuardDataBeet: beet.FixableBeetArgsStruct<CandyGuardData>;
