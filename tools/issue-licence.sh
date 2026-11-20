#!/usr/bin/env bash
# issue-licence.sh — DeryCode's OFFLINE licence issuer.
#
# The private key never goes in the app or the repo. Keep it on a USB stick or in
# a password manager; if it leaks, someone can mint licences, and the only fix is
# a new keypair shipped in a new build.
#
# Usage:
#   ./issue-licence.sh <PLAN> <DAYS> <SHOP-ID> [private-key.pem]
#
#   PLAN     two-letter plan code: BA (basic), PR (pro), EN (enterprise), HT (hotel)
#   DAYS     licence length, 1-3650 (counted from the moment the shop activates)
#   SHOP-ID  short shop identifier, no spaces, e.g. KAMPALA-MAIN
#
# Example:
#   ./issue-licence.sh PR 365 KAMPALA-MAIN ~/keys/deryaccount-licence-private.pem
#
set -euo pipefail

PLAN="${1:?plan code required (BA|PR|EN|HT)}"
DAYS="${2:?days required (1-3650)}"
SHOP="${3:?shop id required, e.g. KAMPALA-MAIN}"
KEY="${4:-deryaccount-licence-private.pem}"

[[ "$DAYS" =~ ^[0-9]+$ ]] || { echo "days must be a number" >&2; exit 1; }
(( DAYS >= 1 && DAYS <= 3650 )) || { echo "days must be between 1 and 3650" >&2; exit 1; }
[[ -f "$KEY" ]] || { echo "private key not found: $KEY" >&2; exit 1; }

PAYLOAD="${PLAN}|${DAYS}|${SHOP}|$(date -u +%Y%m%d)"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
printf '%s' "$PAYLOAD" > "$TMP/payload"

# ECDSA P-256 / SHA-256, DER signature — exactly what the app verifies.
openssl dgst -sha256 -sign "$KEY" -out "$TMP/sig" "$TMP/payload"

# base32 (RFC 4648, no padding) — the same alphabet the app decodes.
b32() {
  python3 -c "import sys,base64;print(base64.b32encode(open(sys.argv[1],'rb').read()).decode().rstrip('='))" "$1"
}

echo "DERY2-$(b32 "$TMP/payload")-$(b32 "$TMP/sig")"
echo "" >&2
echo "payload: $PAYLOAD" >&2
echo "Hand this code to the shop. It activates offline and cannot be generated without the private key." >&2
