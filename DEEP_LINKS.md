
# Setup

Make sure all the release keys are included in the deep link config at `$BRINGYOUR_HOME/web/ur.io/.well-known/assetlinks.json`

```
# e.g.
keytool -list -v -keystore $BRINGYOUR_HOME/release/android/signing/app.jks  -alias solana_dapp
```

Also make sure developer debug keys are included.

```
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```


# Google SSO

Make sure your debug key is added as an Android login.
