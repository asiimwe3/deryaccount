# DeryAccount — Remediation Plan and Stage 1 Evidence

**Repo:** `asiimwe3/deryaccount` @ `97956a4` (phone 1.3.0) · **Date:** 2026-11-20
**Branch:** `remediation/stage-1` · **No release published, no production service touched.**

Statuses: `Not Started` · `In Progress` · `Fixed` · `Tested` · `Verified`
(`Tested` = automated test proves it. `Verified` = additionally checked on a real device / live backend.)

## Audit reality check (what the code actually shows)

| Claim in the audit | What the repo contains today |
|---|---|
| License signing secret in client | **Confirmed.** `billing/License.kt:103` and `billing/HotelLicence.kt:36` both hardcode `SECRET = "DeryAccount-Offline-License-v1-2026-KampalaUG"` and verify HMAC-SHA256 locally. Anyone with the APK can mint valid codes. |
| POS partial payment wrong | **Partly.** `PosRepository.checkout` only treated unpaid amounts as debt when `paymentMethod == "CREDIT"`; a part-paid cash/MoMo sale silently lost the debt. |
| VAT wrong | **Confirmed and worse than reported.** `PosRepository:53` had `val taxTotal = 0.0` — VAT was never charged, and `acc-vatout` was never posted to. `Product.taxRate` existed but was unused. |
| Migrations destructive | **Not confirmed.** No `fallbackToDestructiveMigration` anywhere; `AppDatabase` declares explicit `MIGRATION_2_3 … MIGRATION_9_10` up to `version = 10`. |
| Journal posting not transactional | **Confirmed.** `AccountingRepo.post()` wrote the entry header and its lines in two separate DAO calls, outside any transaction. |
| Duplicate receipts | **Confirmed.** `generateReceiptNo()` counted sales *before* `withTransaction`, so two tills could take the same number. |
| No tests | **Confirmed.** `app/src/test` and `app/src/androidTest` did not exist; no test dependencies. |
| Windows desktop packaging | **Not in this repo.** There is no desktop module — `deryaccount` is a single-module Android app. Desktop claims need a different repo or don't exist. |

## Remediation checklist

| # | Finding | Status | Evidence |
|---|---|---|---|
| 3 | VAT calculations | **Tested** | `Money.vatInclusive/totals`, `postSale(tax=)`, 15 unit tests |
| 12 | Transactional, balanced journal | **Tested** | `AccountingRepo.post()` wrapped in `withTransaction`, `Money.requireBalanced` |
| 2 | POS partial-payment accounting | **Tested** | `Money.outstanding/changeDue`, debt recorded for every method |
| 13 | Duplicate receipt numbers | **Fixed** | receipt allocated inside the sale transaction; **unique index still to add (needs migration)** |
| 6 | Password/PIN hashing | **Tested** | `security/PinHasher` (PBKDF2 150k, per-user salt), legacy upgrade-on-login, `security/LoginThrottle` — 8 unit tests |
| 1 | Licensing secrets | **Tested** | `security/LicenceCode` (ECDSA P-256, public key only in app) + `tools/issue-licence.sh`; secret removed from both licence files |
| 4 | Offline sync coverage | Not Started | sync engine exists; per-module coverage not yet audited |
| 5 | Destructive migration fallback | **Fixed** | Correction to the first report: `.fallbackToDestructiveMigration()` DID exist (I said it did not). It is now `.fallbackToDestructiveMigrationFrom(1)`, so only the pre-accounting v1 schema can be wiped; any other missing migration fails loudly instead of deleting books. |
| 7 | Branch isolation | **In Progress** | DAOs already take branchId for products, sales totals, barcode and search; `observeLowStock()`/`observeReorder()` did not, so alerts leaked other shops. Branch-scoped variants added and HomeScreen now uses `observeReorder(branchId)`. Remaining: reports that call `totalAllBranches` regardless of role, and hotel/inventory repositories. |
| 8 | Role permissions | **In Progress** | Permissions were `role == "CASHIER"` string checks in three screens. `security/AccessPolicy.kt` now holds a capability matrix (8 tests); drawer and Business Tools gates ask it. Remaining: enforce inside repositories so a screen cannot bypass the policy. |
| 9 | Logout clears business settings | **Fixed** (compile-verified) | `logout()` now removes only the 5 session keys |
| 10 | Backup and restore | **Tested** | Backup checkpoints the WAL, writes `.part` then renames, and carries a SHA-256 manifest. New `data/local/BackupRestore.kt` plus a "Restore from a backup" card in BackupScreen let a shop pick a zip from Drive or a chat: it is opened, the database is checked against the manifest and with SQLite `integrity_check`, and only then staged as `restore_pending.db` for the startup swap, which keeps a pre-restore safety copy. 9 integrity tests. Still needs a physical-device pass. |
| 11 | Auto-update asset names | **Fixed** (unverified) | `AutoUpdate.kt` points at `releases/latest/download/deryaccount.apk`; must confirm the release actually publishes that filename |
| 14 | Hotel check-in/out consistency | Not Started | needs `withTransaction` audit of hotel repo |
| 15 | Branch attribution on auto entries | Not Started | `post()` accepts `branchId = null` |

## Stage 1 — what changed and why

### 1. `accounting/Money.kt` (new)
The single place that decides rounding, VAT extraction, outstanding balances, change and receipt format. Pure Kotlin, no Android imports, so it is unit-testable on the JVM.

- **VAT-inclusive pricing** (the Ugandan retail convention): a 118,000 shelf price at 18% *contains* 18,000 of VAT. `vatInclusive()` extracts it; it never adds tax on top of a shelf price.
- Per-line tax, summed: a basket mixing exempt and rated goods is exact to the cent.
- Invariant: `total = netRevenue + tax - discount`, and `subtotal = total + discount`.

### 2. `data/repository/PosRepository.kt`
- `taxTotal` is computed from each product's own `taxRate` (0 = exempt) instead of being hardcoded to `0.0`.
- Totals, change and outstanding balance all come from `Money`, so the receipt, the customer's balance and the ledger cannot disagree.
- Part payment now creates a debtor balance **on every payment method**, not only `CREDIT` (a customer who paid 40,000 of 100,000 on MoMo was previously recorded as fully settled).
- `receivedNow = amountPaid - change`, so an overpaid cash sale does not inflate `totalPaid`.
- Receipt numbers are allocated **inside** `withTransaction`, where Room serialises writers, so concurrent checkouts cannot take the same sequence.
- The VAT split is passed to `postSale`, and `CheckoutResult` returns the receipt number that was actually allocated.

### 3. `accounting/AccountingRepo.kt`
- `post()` now rounds every line through `Money`, rejects unbalanced or zero-value entries **before** touching the database, and writes the header and lines in one `withTransaction` (a crash between the two used to leave an orphan entry that broke every trial balance).
- `postSale(..., tax)` credits **VAT Payable** and books **Sales net of VAT**, so revenue on the P&L is real revenue and the VAT liability is visible.

### Database / migration impact
**None.** No schema change, no new table, no migration, no data rewrite. `Product.taxRate`, `Sale.taxTotal` and the `acc-vatout` account already existed. Existing sales are untouched: their `taxTotal` stays whatever it was (0), which is historically correct — no retro-posting was attempted.

### Tests executed
`./gradlew :app:testDebugUnitTest` → **15 tests, 0 failures, 0 errors** (`app/src/test/.../MoneyTest.kt`).

Covered: VAT extraction (18%, exempt, sub-shilling rounding), mixed-basket reconciliation, discount-vs-tax interaction, net+tax=gross, part payment, change, balanced/unbalanced rejection, a full sale posting with VAT and discount, receipt formatting and past-9999 ordering.

Two of these tests **failed on the first run** and exposed a genuine bug in my own first draft: `totals()` added tax on top of an inclusive subtotal (128,000 instead of 110,000). The rule was corrected to inclusive-pricing and the tests then passed — recorded here because that is exactly the kind of error that would have reached a shop.

**Not executed (needs device/emulator/backend):** Room instrumented tests, concurrent-checkout stress, sync round-trip, backup/restore, Bluetooth printing, Android install/update, role-based permission walks.

### Remaining risks in Stage 1
1. **Receipt numbers are still not enforced unique at the database level.** The transaction removes the realistic race, but a deleted sale or a manual data import can still reuse a number. A unique index on `(branchId, receiptNo)` needs a migration with a pre-flight duplicate scan.
2. **Historical VAT.** Past sales carry `taxTotal = 0`. Reports for those periods show no VAT. That is accurate for what was charged, but the business may owe VAT on turnover; this needs an accountant's decision, not code.
3. **Tax rates are per product only.** There is no business-wide default rate, so products created before this change still have `taxRate = 0` and will remain exempt until set.
4. **`postSale` is called with `branchId = null`** (finding 15) — the ledger entries are not yet attributed to a branch.

## Stage 2 — authentication and session (findings 6, 9)

### `security/PinHasher.kt` (new)
PINs were a bare `SHA-256(pin)`. A 4-digit PIN is 10,000 guesses, so that hash is reversible on the phone in well under a second — anyone who copied the database could read every cashier's PIN.

- Stored format: `pbkdf2$<iterations>$<saltHex>$<hashHex>`, PBKDF2-HMAC-SHA1, 150,000 iterations, 16-byte per-user random salt, 256-bit output.
- Salt means the same PIN never produces the same stored value, so a stolen table cannot be cross-referenced between shops or staff.
- Comparison is constant-time.
- **Legacy hashes still verify**, so no existing user is locked out; the record is re-hashed to PBKDF2 the moment that PIN is entered correctly (`needsUpgrade`).
- Corrupt records fail closed instead of throwing, so a bad row cannot crash login.
- Hex encoding, not `java.util.Base64`: that class needs Android 8 and this app still runs on Android 7 tablets (minSdk 24).

### `security/LoginThrottle.kt` (new)
Five wrong PINs lock that **username** for 60 seconds, counted in SharedPreferences so killing the app does not reset it. A correct PIN clears the counter. Locking is per user, so a careless cashier cannot lock the owner out.

### `ui/auth/LoginScreen.kt`
- First-run owner creation stores `PinHasher.hash(pin)`.
- Sign-in verifies through `PinHasher`, reports remaining attempts, blocks while locked, and upgrades legacy hashes on success.
- `LocalContext` bound for the throttle.

### `util/SessionManager.kt`
`logout()` called `dataStore.edit { it.clear() }`, wiping the business profile, logo, TIN, receipt footer, Supabase credentials and tutorial flag — a cashier tapping "Log out" reset the shop's identity. It now removes exactly the five session keys (`user_id`, `username`, `role`, `branch_id`, `branch_name`).

### Tests executed
`./gradlew :app:testDebugUnitTest` → **23 tests, 0 failures** (15 money + 8 PIN).
`./gradlew :app:compileDebugKotlin` → success.

PIN tests prove: correct PIN verifies; wrong PIN, longer PIN and empty PIN rejected; stored value never contains the plain SHA-256; same PIN hashes differently each time (salt); legacy SHA-256 record still verifies and is flagged for upgrade; corrupt/`null` records fail closed; a legacy hash cannot be replayed as a PBKDF2 record.

**Not executed:** throttle timing on a device, and the login screen itself (no emulator here).

### Remaining risks in Stage 2
1. **Iteration count is untested on low-end hardware.** 150,000 PBKDF2 rounds may take ~300-600 ms on the cheapest tablets; if login feels slow on the shop's actual phone, lower it to 100,000 and re-measure rather than guessing.
2. **Throttle is per device, not per account across devices** — acceptable offline, but it is not a substitute for server-side lockout once sync is live.
3. **No forced PIN reset** for staff whose PIN was set by someone else; and PINs are still 4 digits, so entropy stays low even with a good KDF.
4. `SessionManager.sha256` still exists for other call sites; it should be deleted once nothing else depends on it.

## Stage 3 — licensing without a shipped secret (finding 1)

The app used to carry the HMAC **signing** secret in `License.kt` and `HotelLicence.kt`, so anyone who unpacked the APK could mint unlimited valid licences.

- **New scheme:** `DERY2-<base32 payload>-<base32 signature>`, payload `PLAN|DAYS|SHOP|YYYYMMDD`, signed with ECDSA P-256 / SHA-256. The app holds only the **public** key (`LicenceCode.PUBLIC_KEY_HEX`, hex because `java.util.Base64` needs Android 8 and minSdk is 24). A public key can verify but cannot create.
- **Issuer:** `tools/issue-licence.sh` signs offline with the private key. It lives at `licence-keys/deryaccount-licence-private.pem` in the workspace, **outside the repo and outside git**. Whoever holds it can mint licences, so it must be moved to a USB stick or password manager.
- **No shop is locked out:** legacy 16-character HMAC codes still verify, but only until `LEGACY_SUNSET = 2027-06-30`. After that date a shop must hold a DERY2 code. The cutoff exists because the legacy secret is still inside old APKs, so leaving the old verifier open for ever would keep the hole open for ever.
- **In-app generation removed:** `LicenseKeys.generate()` is deleted and `generateCodeForSupport()` / `HotelLicence.issueCode()` now return an empty string instead of a code signed by the app. No UI calls them (grep-verified), so nothing in the app breaks.

### Tests executed
- `./gradlew :app:testDebugUnitTest` → **33 tests, 0 failures** (15 money, 8 PIN, 10 licence).
- Licence tests prove: a properly signed code verifies and yields its payload; a tampered payload is rejected even with the original signature attached; a code signed by a different key is rejected; garbage and out-of-range durations fail closed; the embedded public key parses as a valid P-256 key; legacy HMAC codes verify before the sunset and are rejected after it.
- **End-to-end, actually run:** a code issued by `tools/issue-licence.sh` with the real private key was verified two ways — by `openssl dgst -verify` against the public key, and by the app's own `LicenceCode.verify()` through a temporary JVM test (passed, then removed). The embedded key was confirmed byte-identical to `deryaccount-licence-public.pem`.

### Remaining risks in Stage 3
1. **The legacy secret is still in the binary** for backward compatibility until 2027-06-30. A determined attacker can still forge legacy codes during that window. The only complete fix is dropping legacy support after reissuing licences, which is a business decision plus a comms task.
2. **One keypair for all products.** Licence and hotel unlock share it. Rotating means shipping a new public key in a new build; there is no key-rotation path yet (two-key list would fix it).
3. **Codes are long** (~120 chars) because they carry a real signature. Fine for paste/WhatsApp, awkward for reading over the phone.
4. **No revocation.** An offline licence cannot be cancelled once issued; expiry is the only lever.
5. **Private key custody is now the single point of failure.** If it is lost, no new licences can be issued with this build; if it leaks, licences can be forged.

## Recommended next steps, in risk order

1. ~~Auth hardening (finding 6)~~ — done in Stage 2, pending device timing check.
2. ~~Licensing (finding 1)~~ — done in Stage 3 with ECDSA P-256 (Ed25519 is not available on Android 7, which this app still supports). Legacy codes sunset 2027-06-30.
3. **Branch isolation and permissions (7, 8).** Enforce `branchId` at the DAO level (no query without a branch predicate) and check role in the repository, not the screen.
4. **Unique receipt index (13).** Migration 10→11 with a duplicate pre-scan that reports, never deletes.
5. **Backup/restore (10).** Write to `.tmp`, verify by reopening with Room, then atomically swap; keep the previous file as `.bak`.
6. **Hotel transactions (14).** Wrap check-in, checkout, folio posting and night audit in `withTransaction`, and add a uniqueness guard on room-nights to stop double booking.

## Definition of completion — current position
Not production-ready. Stage 1 fixes money arithmetic and ledger integrity (findings 2, 3, 12, 13-partial). Auth, licensing, branch isolation, permissions, sync, backup and hotel concurrency remain open. Per the brief, no release will be published without explicit approval.
