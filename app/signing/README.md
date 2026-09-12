# Private APK signing

Never commit an APK signing key or its passwords to GitHub.

Without private signing values, `assembleDebug` uses Android's ordinary local debug key. To produce an update that Android accepts over an already installed Anki Match build, provide the same private key used for that installed build through local Gradle properties or environment variables:

```properties
ANKIMATCH_KEYSTORE_PATH=/absolute/path/to/ankimatch.p12
ANKIMATCH_KEYSTORE_PASSWORD=...
ANKIMATCH_KEY_ALIAS=...
ANKIMATCH_KEY_PASSWORD=...
```

The four names can also be stored as encrypted GitHub Actions secrets. The keystore itself must remain outside the public repository.
