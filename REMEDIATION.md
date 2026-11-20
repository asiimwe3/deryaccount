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
| 6 | Password/PIN hashing | Not Started | `SessionManager.sha256(pin)` — unsalted SHA-256, trivially brute-forced for 4-digit PINs |
| 1 | Licensing secrets | Not Started | needs asymmetric signing + offline-issued codes; plan below |
| 4 | Offline sync coverage | Not Started | sync engine exists; per-module coverage not yet audited |
| 5 | Migration safety | Not Started (low risk) | migrations are explicit; needs `MigrationTestHelper` coverage |
| 7 | Branch isolation | Not Started | needs query-by-branch audit across hotel/inventory/reports |
| 8 | Cashier/staff permissions | Not Started | role checks are UI-level; repository has no guard |
| 9 | Logout clears business settings | Not Started | `SessionManager.logout()` to be read and fixed |
| 10 | Backup/restore atomicity | Not Started | needs write-to-temp + validate + swap |
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

## Recommended next steps, in risk order

1. **Auth hardening (finding 6).** Replace unsalted SHA-256 with PBKDF2/Argon2 + per-user salt + migration-on-next-login, add PIN attempt throttling. Highest real-world exposure for a shop full of staff.
2. **Licensing (finding 1).** Move to Ed25519: private key stays offline (in the licence tool), public key ships in the app. Accept legacy HMAC codes during a transition window so existing activations keep working.
3. **Branch isolation and permissions (7, 8).** Enforce `branchId` at the DAO level (no query without a branch predicate) and check role in the repository, not the screen.
4. **Unique receipt index (13).** Migration 10→11 with a duplicate pre-scan that reports, never deletes.
5. **Backup/restore (10).** Write to `.tmp`, verify by reopening with Room, then atomically swap; keep the previous file as `.bak`.
6. **Hotel transactions (14).** Wrap check-in, checkout, folio posting and night audit in `withTransaction`, and add a uniqueness guard on room-nights to stop double booking.

## Definition of completion — current position
Not production-ready. Stage 1 fixes money arithmetic and ledger integrity (findings 2, 3, 12, 13-partial). Auth, licensing, branch isolation, permissions, sync, backup and hotel concurrency remain open. Per the brief, no release will be published without explicit approval.
