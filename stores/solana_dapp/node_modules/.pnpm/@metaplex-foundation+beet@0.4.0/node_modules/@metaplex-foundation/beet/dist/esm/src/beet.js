import { collectionsTypeMap, } from './beets/collections';
import { compositesTypeMap, } from './beets/composites';
import { numbersTypeMap, } from './beets/numbers';
import { stringTypeMap } from './beets/string';
import { enumsTypeMap } from './beets/enums';
import { aliasesTypeMap, } from './beets/aliases';
export * from './beets/aliases';
export * from './beets/collections';
export * from './beets/string';
export * from './beets/composites';
export * from './beets/enums';
export * from './beets/numbers';
export * from './beet.fixable';
export * from './read-write';
export * from './struct';
export * from './struct.fixable';
export * from './types';
/**
 * Maps all {@link Beet} de/serializers to metadata which describes in which
 * package it is defined as well as which TypeScript type is used to represent
 * the deserialized value in JavaScript.
 *
 * @category TypeDefinition
 */
export const supportedTypeMap = {
    ...collectionsTypeMap,
    ...stringTypeMap,
    ...compositesTypeMap,
    ...enumsTypeMap,
    ...numbersTypeMap,
    ...aliasesTypeMap,
};
//# sourceMappingURL=beet.js.map