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
