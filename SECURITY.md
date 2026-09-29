# Security Policy — MonteCarloLedger-Android

## Project status: proprietary, not open source

MonteCarloLedger-Android is proprietary software. The source is published for source visibility and transparency only. The repository [LICENSE](LICENSE) is a proprietary license notice that grants no permission to copy, modify, redistribute, or build derivative works. The [README](README.md) states this in its opening banner.

Because no permission to use the code has been granted, a defect in it is not a "vulnerability" in the open-source sense. It is a question about unauthorized use of unlicensed software, and that question belongs to the owner of the code, not to a public disclosure process. This file exists so the boundary is stated plainly instead of left to inference.

## What this repository does not offer

- **No security support.** The maintainer does not triage, investigate, or remediate security reports for MonteCarloLedger-Android.
- **No coordinated disclosure program.** There is no embargo, no safe harbor, and no private disclosure window.
- **No bug bounty.** No reward is offered.
- **No response-time commitment.** There is no SLA and no support window.
- **No supported versions.** No release is a supported security-fix channel.

## Why this repository deserves extra care from a reader

This is a finance application. It stores real money records, uses AES-GCM for encrypted backups, and gates access behind an App Lock PIN. A confidentiality or integrity failure here is materially more damaging than a similar defect in a hobby project. That is a statement about consequence, not about what the maintainer has committed to.

Nothing in this file should be read as a claim that the implementation is correct, reviewed, or audited.

## Documented integrity controls

- [PRIVACY.md](PRIVACY.md) — local-only data model: no account, no `INTERNET` permission, on-device Room database, user-initiated SAF export, and an explicit statement that the App Lock PIN is stored as a salted hash on-device only and is not emitted in plaintext JSON or in encrypted backup files.
- [docs/SHIP_STANDARD.md](docs/SHIP_STANDARD.md) — the normative ship process. It classifies money math, Room, security and backup, lock, and forecast changes as **HIGH** surfaces requiring explicit human approval after structured review, and forbids committing `local.properties`, keystores, `.env*`, or secrets. It also defines a verification ladder in which a documentation-only change is tier **V0**, human review of links and claims.
- [QA_CHECKLIST.md](QA_CHECKLIST.md) and [STATUS.md](STATUS.md) — the project's own verification and status record.

These are point-in-time records of the project's own process, not a guarantee that no defect exists.

## Reporting a genuine concern

If you believe you have found a genuine security concern — especially one involving backup encryption, PIN handling, or ledger integrity — the honest position is that the maintainer has not accepted a support obligation, so there is no guaranteed response. If you choose to raise it anyway:

- Prefer GitHub's private vulnerability reporting for this repository (the **Security** tab → **Report a vulnerability**), if it is available to you.
- Otherwise contact the repository owner through their public profile at <https://github.com/gthgomez>.
- Do not open a public issue. Do not include real financial data, account identifiers, or working exploit code in a report.
- You receive no service commitment, no bounty, and no assurance of a fix.

## Visibility is not permission

The repository being public creates no support obligation. Publishing source does not grant a license, does not create a support contract, and does not make the maintainer a vendor to you. Opening an issue or submitting a pull request grants you no rights and creates no partnership; contributions are not accepted for reuse, and no license is granted over anything you send here.
