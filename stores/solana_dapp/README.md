
# Steps to publish

https://github.com/solana-mobile/dapp-publishing
https://solana.com/docs/core/clusters

https://docs.solanamobile.com/dapp-publishing/publishing_releases


```

npx dapp-store create release -k ~/bringyour/vault/android/solana_dapp/keypair.json -b $ANDROID_HOME/build-tools/34.0.0 -u https://api.mainnet-beta.solana.com

npx dapp-store publish update -k ~/bringyour/vault/android/solana_dapp/keypair.json --complies-with-solana-dapp-store-policies --requestor-is-authorized -u https://api.mainnet-beta.solana.com
```

