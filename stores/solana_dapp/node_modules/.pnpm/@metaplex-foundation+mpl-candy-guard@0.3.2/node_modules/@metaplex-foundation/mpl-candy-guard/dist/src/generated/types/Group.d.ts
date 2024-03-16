import * as beet from '@metaplex-foundation/beet';
import { GuardSet } from './GuardSet';
export declare type Group = {
    label: string;
    guards: GuardSet;
};
export declare const groupBeet: beet.FixableBeetArgsStruct<Group>;
