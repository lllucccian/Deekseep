#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION=2026.6.0
MIRROR="${TERMUX_CLOUDFLARED_MIRROR:-http://mirror.mephi.ru/termux/termux-main}"
FETCH_DIR="$(mktemp -d)"
DEST=third_party/cloudflared/android

fetch_one() {
  local package_arch="$1"
  local android_abi="$2"
  local expected_package_sha256="$3"
  local package_file="$FETCH_DIR/cloudflared_${package_arch}.deb"
  local unpacked="$FETCH_DIR/root_${package_arch}"
  local source

  curl -fL --retry 3 \
    -o "$package_file" \
    "$MIRROR/pool/main/c/cloudflared/cloudflared_${VERSION}_${package_arch}.deb"
  printf '%s  %s\n' "$expected_package_sha256" "$package_file" | sha256sum -c -
  dpkg-deb -x "$package_file" "$unpacked"
  source="$unpacked/data/data/com.termux/files/usr/bin/cloudflared"
  test -x "$source"
  install -d "$DEST/$android_abi"
  install -m 0755 "$source" "$DEST/$android_abi/libcloudflared.so"
}

fetch_one aarch64 arm64-v8a \
  e844786f86a356a9145b8294a2a98da2fde57844a4d80222b18d5b38f9ee1416
fetch_one arm armeabi-v7a \
  3f6dd373d24d362fe1ce811f0bf38b5e337de3672aead5690b15179a5c4969c2
fetch_one x86_64 x86_64 \
  cec9a80c357ead377d5e175c895b31e25e88796a38647d65d4d2400fbfe61d4e
fetch_one i686 x86 \
  a7884032713d568a182e718038350fd2912901a226f1c1f54b634f9c2b54f0ac

sha256sum "$DEST"/*/libcloudflared.so
echo "cloudflared Android binaries ready in $DEST"
