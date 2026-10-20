#!/usr/bin/env zsh

# Load the release signing variables the user configured in ~/.zshrc.
source /home/sugeng/.zshrc >/dev/null 2>&1

unsetopt xtrace verbose
setopt errexit nounset pipefail
cd "${0:A:h}"

if [[ -z "${SIGNING_KEY:-}" || -z "${ALIAS:-}" || -z "${KEY_STORE_PASSWORD:-}" || -z "${KEY_PASSWORD:-}" ]]; then
    print -u2 "Missing release signing variables in ~/.zshrc."
    exit 1
fi

if [[ "$SIGNING_KEY" == '~/'* ]]; then
    SIGNING_KEY="$HOME/${SIGNING_KEY#'~/'}"
elif [[ "$SIGNING_KEY" == '$HOME/'* ]]; then
    SIGNING_KEY="$HOME/${SIGNING_KEY#'$HOME/'}"
fi

typeset keystore_path="$SIGNING_KEY"
typeset temporary_keystore=""
if [[ ! -f "$keystore_path" ]]; then
    temporary_keystore="$(mktemp "${TMPDIR:-/tmp}/komiyomi-ext-release.XXXXXX.jks")"
    trap 'if [[ -n "$temporary_keystore" ]]; then rm -f -- "$temporary_keystore"; fi' EXIT
    if ! print -rn -- "$SIGNING_KEY" | base64 --decode > "$temporary_keystore" 2>/dev/null; then
        print -u2 "SIGNING_KEY is neither an existing keystore path nor valid base64 keystore data."
        exit 1
    fi
    if [[ ! -s "$temporary_keystore" ]]; then
        print -u2 "Decoded release keystore data is empty."
        exit 1
    fi
    keystore_path="$temporary_keystore"
fi

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/mnt/android/sdk}}"
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
if [[ ! -x "$apksigner" ]]; then
    print -u2 "Android build-tools 36.0.0 apksigner was not found."
    exit 1
fi

# KMK -->
if [[ "${SKIP_GRADLE_BUILD:-0}" != "1" ]]; then
    ./gradlew -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' assembleRelease
fi
# KMK <--

typeset -i signed_count=0
for unsigned_apk in app/build/outputs/apk/release/*-release-unsigned.apk; do
    [[ -f "$unsigned_apk" ]] || continue
    signed_apk="${unsigned_apk%.apk}-signed.apk"
    temporary_apk="${signed_apk%.apk}.tmp.apk"

    "$apksigner" sign \
        --ks "$keystore_path" \
        --ks-key-alias "$ALIAS" \
        --ks-pass env:KEY_STORE_PASSWORD \
        --key-pass env:KEY_PASSWORD \
        --out "$temporary_apk" \
        "$unsigned_apk"
    "$apksigner" verify --verbose "$temporary_apk" >/dev/null
    mv -f "$temporary_apk" "$signed_apk"
    if [[ -f "$temporary_apk.idsig" ]]; then
        mv -f "$temporary_apk.idsig" "$signed_apk.idsig"
    fi

    print "Signed and verified: $signed_apk"
    (( signed_count += 1 ))
done

if (( signed_count == 0 )); then
    print -u2 "No release APKs were found to sign."
    exit 1
fi

print "Completed: $signed_count release APKs signed and verified."
