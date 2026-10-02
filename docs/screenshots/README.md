# Screenshots

This directory holds the screenshots referenced by the root `README.md`.

## Captured screens

| File | Screen | Route |
|---|---|---|
| `login.png` | Sign-in | `/login` |
| `register.png` | Registration with password strength | `/register` |
| `dashboard.png` | Customer dashboard | `/dashboard` |
| `accounts.png` | Account list | `/accounts` |
| `account-detail.png` | Account detail with balance chart | `/accounts/:id` |
| `transfer.png` | Transfer flow, recipient + amount | `/transfer` |
| `transfer-review.png` | Transfer review before sending | `/transfer/review` |
| `transfer-success.png` | Transfer completed | `/transfer/success/:id` |
| `transactions.png` | Transaction table with filters | `/transactions` |
| `transaction-detail.png` | Transaction detail with event timeline | `/transactions/:id` |
| `notifications.png` | Notification centre | `/notifications` |
| `security.png` | Security centre | `/security` |
| `admin-dashboard.png` | Administration overview | `/admin` |
| `admin-transactions.png` | Admin transaction monitor | `/admin/transactions` |
| `fraud-dashboard.png` | Fraud & risk operations | `/admin/fraud` |
| `fraud-detail.png` | Fraud alert with timeline and actions | `/admin/fraud/:id` |
| `kibana-fraud.png` | ELK fraud events dashboard | http://localhost:5601 |

## How to capture them

Run the platform, sign in with a seeded account, and capture at 1440×900
(desktop) and 390×844 (mobile). Mock mode is the fastest route — the SPA has
realistic data with no backend needed:

```bash
cd frontend
npm install
npm run dev          # VITE_USE_MOCKS=true
```

For the Kibana screenshot, run the full stack, import the dashboards
(`infrastructure/elk/kibana/finova-dashboard.ndjson`), send a transfer above
10,000 TND so the fraud path produces a HIGH alert, then open
**Finova — Fraud Events**.

> These files are intentionally not committed. Screenshots in a portfolio
> repository are often stale relative to the code, and a stale screenshot is
> worse than none — it makes the reviewer question everything else too. Capture
> fresh ones from a running instance.