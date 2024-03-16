import { toPublicKey } from '../../types/PublicKey.mjs';

const toMintAddress = value => {
  return typeof value === 'object' && 'mintAddress' in value ? value.mintAddress : toPublicKey(value);
};

export { toMintAddress };
//# sourceMappingURL=helpers.mjs.map
