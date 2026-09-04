# DeryAccount
Offline-first multi-branch accounting & POS for Ugandan shops and supermarkets.

## Built for speed in a busy shop
- **Tap to sell** — big product tiles; tap = in cart, tap again = +1. No typing.
- **Scan anything** — known barcode auto-adds; unknown barcode opens a 2-field instant product creator.
- **One-tap cash** — big CASH button, quick note buttons (10k/20k/50k/Exact) for change.
- **Everything works with NO internet** — local Room DB is the source of truth.

## On-device safe storage
A visible `DeryAccount` folder is created on the device:
- `receipts/` — every sale auto-saved as a text receipt
- `backups/` — full database backup (Settings → Backup)
- `reports/` — end-of-day Z-reports

## Architecture
- Kotlin + Jetpack Compose (Material 3)
- Room database — 14 tables (sales, stock, customers, suppliers, expenses, shifts, transfers…)
- Atomic checkout engine — stock, movements, and cash update together or not at all
- ESC/POS Bluetooth thermal printer support (58mm/80mm)
- Supabase sync engine for multi-branch (optional — single branch runs fully offline)
- Receipt numbers: `KLA-20260904-0001` style, zero-padded per branch

## Roadmap
- Camera barcode scanning
- Expenses & cash shift reconciliation screens
- Head-office multi-branch dashboard

## Books of Account (v0.2) — 100% offline, no external connection
The **Books** tab gives the traditional formats bookkeepers already know:
- **Cash Book** — receipts & payments with opening B/F and running balance
- **Petty Cash** — petty cash account included in the chart of accounts
- **Ledger** — per-account T-entries with running balance
- **Trial Balance** — Dr = Cr must always balance (enforced by the engine)
- **Income Statement** — income, expenses, net profit/loss for the month

Chart of accounts (1000 Cash, 1001 Petty Cash, 1010 Bank/MoMo, 1100 Debtors,
1200 Stock, 2000 Creditors, 3000 Capital, 3100 Drawings, 4000 Sales,
5000 Cost of Sales, 5100 Purchases, 5200–5900 expense accounts).

Every POS sale posts automatically: Dr Cash/Bank/Debtors, Cr Sales.

## Quick Stock Setup (v0.3)
Adding stock now starts with a business category picker:
General Shop, Supermarket, Hardware, Pharmacy, Agro-Vet, Butchery, Salon,
Stationery, Bar & Restaurant, Mobile Money & Airtime.
Each category ships with a ready list of 20–36 common Ugandan items (with
typical prices). The owner ticks items, edits price, enters opening stock,
and adds everything in one tap — opening stock is recorded as a purchase.

## v0.4 — Full books, printing, PDF
- 12 business categories, 800+ catalog items with typical UGX prices
- Price & stock fields always editable in the picker (auto-ticks on typing)
- Payment dialog shows clear errors (no silent failures)
- Calculator at the POS counter
- Hero banner + side drawer navigation (free-for-100 message)
- Balance Sheet added (Assets = Liabilities + Equity)
- PDF engine: receipt auto-saved & auto-printed on every sale, invoices,
  stock reports, and all books of account print via the Android system
  print dialog (Bluetooth/wifi printers) or save as PDF in DeryAccount/documents

## v0.4.1 — purchases posted to the books
Adding stock (quick setup wizard or quick-add from a barcode) now posts a
journal entry automatically: Dr Stock on Hand, Cr Cash — valued at the
entered price x qty. The Trial Balance and Balance Sheet stay complete.

## v0.5 — everything matches, camera scanner, edit & delete
- Camera barcode scanner (ML Kit, fully offline) — tap the scan icon in the search bar
- Cost price captured per item (picker + quick-add + edit)
- Every sale now posts Cost of Sales (Dr COGS, Cr Stock at cost x qty),
  so the Stock account in the Balance Sheet always equals the Stock screen value
- Stock value (closing stock at cost) shown in the Stock screen header
- POS tiles show remaining stock ("12 left", red when low, OUT when empty)
- Edit & delete products: pencil/trash icons on every stock row;
  edits adjust the books (Dr/Cr Stock) and deletes write off remaining stock value
- Picker price/stock fields rebuilt on Compose state maps (typing always visible)
- In-app auto-update: on each launch the app quietly checks this repo's latest
  GitHub release (version.json); if newer, it shows "Update available" and
  downloads + installs in-app — no more manual APK downloads
- Crash safety net: any unexpected crash is logged to DeryAccount/crashes on
  the device; updater and scanner failures never crash the app
