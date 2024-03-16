'use strict';

Object.defineProperty(exports, '__esModule', { value: true });

var PublicKey = require('../../types/PublicKey.cjs');

const toMintAddress = value => {
  return typeof value === 'object' && 'mintAddress' in value ? value.mintAddress : PublicKey.toPublicKey(value);
};

exports.toMintAddress = toMintAddress;
//# sourceMappingURL=helpers.cjs.map
