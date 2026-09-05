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
- Cash shift reconciliation screen (expenses + end-of-day Z-report now shipped in v0.5.1)
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

## v0.5.1 — business profiles & branded receipts
- Settings → Business profile: business name, tagline, phone, location, TIN,
  and a custom thank-you message
- Every receipt (thermal print, PDF, saved text) is printed in the shop's own
  name with its details and its sequential receipt number
  (e.g. DER-20260904-0007 = business code, date, sale of the day)

## v0.5.2 — crash protection for stock saves
- Every stock save (wizard, manual add, edit, adjust, delete) now catches
  errors and shows a clear message instead of crashing to the home screen
- Most common real-world cause: phone storage fills up (app saves a PDF
  receipt per sale). New startup warning appears when space is low
- Exact error details always logged to DeryAccount/crashes for support

## v0.6.0 — enterprise dark redesign
- Fixed dark theme (near-black + forest green) across the whole app
- New Home dashboard: greeting, Total Cash, Today's Sales, Stock Value,
  Today's Expenses — live from the database, no sample data
- Quick actions (New Sale, Add Expense, Add Stock, Cash Book) + Books grid
- New Sales screen: today's sales list with totals, tap for full receipt
  breakdown (items, qty, price, amount, total)
- Cash/Petty/Bank books restyled: opening balance card, ruled table with
  receipts/payments columns + running BALANCE column, totals row,
  closing balance card, separate New Receipt / New Payment buttons
- Bottom nav: Home | Sales | (+ Sell) | Reports | More

## v0.6.1 — post-redesign audit fixes
- "New Payment" button now opens the entry dialog in Paid-out mode
  (previously both buttons opened in Received mode)
- Ledger running balance now starts from the true opening balance
- Home card relabeled "Cash & Bank" (it sums Cash + Petty + Bank/MoMo)

## v0.7.0 — Hold Sale, Favourites, Discount, Reprint
- Hold Sale: park a customer's cart with a note, serve the next one, resume
  later from the pause badge (auto-holds an open cart first, never destroys)
- Favourites: star fast sellers on the POS tiles — they float to the top
- Discount: type it right on the Sell screen (clamped, never negative total)
- Reprint: regenerate any past receipt as PDF via the same engine as checkout
  — print icon on every sale row and in sale details
- New held_sales table + products.isFavourite column via MIGRATION_2_3
  (a real Room migration — shop data survives the update)
- DbSafety crash log now records REAL free disk space (was logging Java heap)

## v0.7.1 — Sell screen redesign (matches latest mockup)
- Status row: branch, cashier name, live Online/Offline pill, manual Sync
- Category filter chips derived from the real catalog (All + top 4 + More menu)
- Products (N) count with Grid/List view toggle; "View more products" paging
- New 2-column product cards: stock count, price, qty stepper, dedicated
  add-to-cart button, favourite star — List view keeps the compact row style
- Cart card: "Cart (N items)" header + Clear Cart, attached-customer line
- Checkout breakdown: Subtotal / Discount (editable) / Tax / TOTAL
- Three equal, colour-coded payment buttons: CASH (green) / MOBILE MONEY
  (amber) / CREDIT (blue), each with a one-line subtitle
- Hold Sale / Customer / Receipt quick-action row under checkout
- New: attach a customer to ANY sale (not just credit) via the Customer button

## v0.7.2 — full screen-by-screen audit fixes
- Home → Books tiles now open the RIGHT book: Cash Book, Petty Cash, Bank & MoMo,
  Ledger, Trial Balance, Income Statement and Balance Sheet each land on their
  own tab/book directly (previously every tile opened the Cash Book)
- Sell screen Sync button now performs a real one-cycle push+pull sync and
  reports the result ("Synced ✓ N up, N down" / offline message) — previously
  it only re-checked connectivity
- Discount box now auto-fills correctly when a held sale with a discount is
  resumed, and clears with the cart

## v0.8.0 — accounting correctness rebuild (ledger auto-generation)
- GENERAL LEDGER now generates itself from the books of original entry, LIVE:
  every POS sale, expense, repayment, stock purchase and manual entry posts
  itself into the Ledger the instant it happens, with proper DEBIT / CREDIT /
  BALANCE columns (no re-typing, no manual posting)
- Cash Book (and Petty / Bank-MoMo books) now update live too — a sale appears
  the moment it's checked out
- Fixed 6 real ledger errors found in the audit:
  1. manual stock adjustments (+/- buttons) posted nothing to the books
  2. editing an item's COST price posted nothing (stock value silently drifted)
  3. POS quick-add posted purchases at RETAIL instead of COST price
  4. the Add Product dialog ignored opening stock in the books entirely
  5. sale posting wasn't atomic with the sale (a failure could leave books
     behind) — now one transaction
  6. ledger/cash-book didn't refresh automatically
- Every save is atomic now: sale, expense, repayment, stock change and product
  creation post to the books in the SAME transaction or not at all
- POS can no longer oversell — quantity is capped at real shelf stock with a
  visible warning (stock and books can never go negative)
- New ledger account 4900 Stock Revaluation Gain (cost-price changes post
  correctly); existing installs receive new accounts automatically
- Built-in book self-check runs on Home: debits = credits, Stock account =
  stock list value, Debtors = customer balances; any mismatch is logged

## v0.8.1 — one-tap error log sharing
- New "Send error logs (WhatsApp)" button in More → Device Storage: packages
  the 5 newest crash logs from DeryAccount/crashes into one text file and
  opens WhatsApp share directly — no file manager needed

## v0.8.2 — fixed the Quick Stock Setup crash (root cause of "app closes when adding stock")
- BusinessCatalog parser crashed with NumberFormatException: catalog lines are
  "|Name|unit|price" (leading pipe from the text block) but the parser read the
  UNIT field ("bag", "kg") as the price. Every category crashed on open.
- Parser rewritten: strips the leading pipe, and any malformed line is skipped
  instead of crashing — a data typo can never take down the shop.
- Verified: all 958 catalog items across 12 categories parse cleanly.

## v0.8.3 — quantity entry always visible in Quick Stock Setup
- The three side-by-side fields (Sell / Cost / Opening stock) were wider than
  the dialog on normal phones — the Opening stock field was pushed off-screen
  and could not be seen or filled. Rebuilt with flexible widths so all fields
  always fit, plus a compact +/- stepper: one tap adds stock, no typing needed.
- Tapping + also ticks the item automatically.

## v0.8.4 — fixed Sales screen crash (Compose animation binary mismatch)
- Crash log showed NoSuchMethodError: KeyframesSpecConfig.at(Object,int) in
  animation-core 1.6.0 when SalesScreen composed its CircularProgressIndicator.
  material3 1.1.2 bytecode calls at() expecting it to return KeyframeEntity;
  1.6.0 moved the method to the base class with a different erased signature.
- Fix: pinned androidx.compose.animation + animation-core to 1.6.7, which
  restores the exact override (verified via javap against both AARs).

## v0.8.5 — fixed Sell screen crash on add-to-cart
- PosScreen used weight(0f) for small carts — Compose forbids zero weights
  (IllegalArgumentException) and the whole Sell screen crashed the moment a
  cart existed with <= 3 items. Weight is now applied only when needed.
