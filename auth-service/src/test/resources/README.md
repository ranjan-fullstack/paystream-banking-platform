# Test-only resources

`test-jwt-keystore.jks` is a throwaway RSA keystore generated solely for
`JwtServiceTest`. It is **never used in any real deployment** -- dev,
staging, or prod all load the real keystore from AWS Secrets Manager via
the Secrets Store CSI driver (see `k8s/secrets-store/paystream-app-secrets.yaml`
and `helm/paystream-service/values/auth-service.yaml`), not from this
directory.

Deliberately distinct from the real keystore so the two can never be
confused:
- Different filename (`test-jwt-keystore.jks`, not `paystream-jwt.jks`)
- Different password (`test-only-password`, not the real keystore's password)
- Certificate DN is literally `CN=TEST ONLY - NOT FOR PRODUCTION, ...`

Regenerate with:
```bash
keytool -genkeypair \
  -alias jwt-signing-key \
  -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
  -keystore auth-service/src/test/resources/test-jwt-keystore.jks \
  -storetype JKS \
  -storepass test-only-password \
  -keypass test-only-password \
  -validity 36500 \
  -dname "CN=TEST ONLY - NOT FOR PRODUCTION, OU=PayStream QA, O=PayStream, L=Test, ST=Test, C=IN"
```
