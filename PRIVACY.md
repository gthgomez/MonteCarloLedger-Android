# MonteCarlo Ledger Privacy Policy

> **Public policy URL (use this in Play Console):**
> <https://raw.githubusercontent.com/gthgomez/MonteCarloLedger-Android/main/PRIVACY.md>
> (human-readable rendering: <https://github.com/gthgomez/MonteCarloLedger-Android/blob/main/PRIVACY.md>)
>
> **Not live yet:** the branch that updates this file has not merged, so `main` still serves
> the earlier *placeholder* policy at this URL. After the signed-release PR merges, the URL
> serves the text below and is final.
> Last verified reachable: 2026-10-11 (HTTP 200) — **re-verify before entering it in Play
> Console** (re-verification due 2026-11-11).

## Summary

MonteCarlo Ledger is a **local-only** personal finance app. Your ledger data stays on your device.

## Data collection

- **No account required.** The app does not create user accounts or collect personal identifiers.
- **No network access.** The app does not declare the `INTERNET` permission and does not transmit your data to our servers.
- **No third-party analytics or ads** in the configuration described here.

## Data storage

All income, bills, transactions, forecasts, and settings are stored locally in an on-device Room database.

## Export and backup

- **Standard export** and **encrypted backup** use Android's Storage Access Framework (SAF). You choose when and where to save files.
- Exports are **user-initiated** only — nothing leaves your device unless you explicitly export it.
- **App Lock PIN** is stored as a salted hash on-device only. The PIN is **not** included in plaintext JSON or encrypted backup files.

## Erase data

You can erase all local data from **Settings → Security & Privacy → Erase all local data**. This permanently deletes on-device ledger data and cannot be undone.

## Contact

Full policy URL: <https://raw.githubusercontent.com/gthgomez/MonteCarloLedger-Android/main/PRIVACY.md>

Once this file is merged to `main`, the repository `gthgomez/MonteCarloLedger-Android` is
public, so that raw URL is publicly viewable without authentication and can be entered
directly in the Play Console "Privacy policy" field. (Until the merge lands, the URL still
serves the placeholder policy — see the note at the top of this file.)
