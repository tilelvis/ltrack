# Signing credentials — SECURITY

## What's stored where

| Where | What |
|-------|------|
| **GitHub Secrets** | `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |
| **Repo (committed)** | Nothing — no keystore, no passwords |
| **Build runner (ephemeral)** | Keystore + `key.properties` are written at build time, exist only for the duration of the build |

## Setup (one-time)

### 1. Generate a keystore locally (do NOT commit it)

```bash
keytool -genkeypair -v \
  -keystore ~/private/loan-tracker-release.jks \
  -alias loantracker \
  -keyalg RSA -keysize 2048 -validity 10000
```

Back this up. Losing it means you can't sign updates.

### 2. Base64-encode it

```bash
base64 ~/private/loan-tracker-release.jks | tr -d '\n' > ~/private/keystore-base64.txt
```

### 3. Add 4 GitHub Secrets

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret name | Value |
|-------------|-------|
| `ANDROID_KEYSTORE_BASE64` | contents of `keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `loantracker` |
| `KEY_PASSWORD` | your key password |

### 4. Push the repo

The workflow (`.github/workflows/build.yml`) has a "Restore Android signing credentials" step that:
- Decodes `ANDROID_KEYSTORE_BASE64` to `keystore/loan-tracker-release.keystore`
- Writes `key.properties` with the other 3 secrets + the correct `storeFile` path
- The `if:` condition skips this step if the secret is absent, so forks without secrets still build (debug-signed)

## If the old keystore was ever committed

Treat it as compromised. Generate a **new** keystore (step 1 above) — don't reuse the old one. The old keystore in git history can't sign updates to the new app (Android refuses cross-certificate updates), but you should still scrub history before making the repo public:

```bash
# Using git-filter-repo (install: pip install git-filter-repo)
git filter-repo --path key.properties --path keystore/ --invert-paths
git push --force origin main
```

Then delete all old GitHub Releases and workflow artifacts that may contain the old APK.
