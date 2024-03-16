import * as beet from '@metaplex-foundation/beet';
import { Creator } from './Creator';
import { Collection } from './Collection';
import { Uses } from './Uses';
export type DataV2 = {
    name: string;
    symbol: string;
    uri: string;
    sellerFeeBasisPoints: number;
    creators: beet.COption<Creator[]>;
    collection: beet.COption<Collection>;
    uses: beet.COption<Uses>;
};
export declare const dataV2Beet: beet.FixableBeetArgsStruct<DataV2>;
