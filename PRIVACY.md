# MonteCarlo Ledger Privacy Policy

**For:** the Android app in this repository (`com.montecarlo.ledger`).
**Reviewed against source:** 2026-11-23.

> **Publication status:** This is policy text kept in the repository. It does not
> claim that a public URL is live, that the app is published, or that a store listing
> exists. The project owner controls publication.

## The short version

- **No account or sign-in is required.** The ledger works on your device.
- **Cloud sync is optional.** It happens only when you start it, using a server,
  vault ID, and passphrase you provide.
- **Standard Export is unencrypted.** Encrypted Backup uses a password you choose.
- **Erase all local data does not erase everything.** Saved export files, a vault copy
  on a server, and the app's saved vault settings are separate.
- **No tracking SDKs in the app dependencies.** This revision does not include an
  analytics, advertising, or crash-reporting SDK.

## Local-first use

The app stores and works with your ledger on the device. Using the ledger, forecasts,
imports, exports, and settings does not require an account or a network connection.

The app's source manifest declares two permissions:

- `INTERNET` — for the optional vault sync described below. The only HTTP client in the
  app source is the vault client.
- `POST_NOTIFICATIONS` — for local reminders, if you turn them on.

The app uses Android's file picker for exports, backups, and CSV imports, so it does not
need broad storage permission. The source manifest does not declare contacts, location,
camera, microphone, or SMS access.

The app's Room database is not separately encrypted by the app. It lives in app-private
storage and relies on Android's app sandbox and the device's protections. App Lock gates
the app screen and reduces detail on the widget and notifications; it is not database
encryption.

The manifest opts out of Android Auto Backup. The app declares exclusions for cloud backup
and device transfer, but their effect has not been verified across Android versions and
devices. Use the app's Encrypted Backup rather than relying on a device transfer.

## Optional encrypted vault sync

Cloud sync uses a vault: an encrypted copy of your core ledger on the configured server.
You can use the rest of the app without ever opening this screen.

Open **Settings → Encrypted cloud sync**. Sync is opt-in and does not run in the
background. The app contacts a vault server only when you choose **Create / join**
(create a vault or join an existing one), **Pull** (download and replace the local core
ledger), or **Push** (upload the local core ledger).

You provide a vault ID (a name for the vault) and a passphrase. If you leave the preset
server address unchanged, requests go to the MonteCarlo Web companion at
`https://montecarlo-ledger.vercel.app`. You may replace that address. For remote servers
the app requires HTTPS and rejects embedded credentials. The source allows a localhost
HTTP address for development; Android can still block cleartext requests.

### What the app sends

During a vault operation, the app exchanges these items with the server:

- the **vault ID**, in the create request or the address for an existing vault;
- an **access token** (a credential made from your passphrase and the vault's salt) — not
  the passphrase itself;
- the **encrypted ledger snapshot and a one-time encryption value (nonce)** — sent when
  creating or pushing, and downloaded when pulling;
- the **salt**, a random value used to derive the encryption key — a new one is made on
  the device for a new vault; pull and push fetch the existing one;
- a **revision number**, used to avoid overwriting newer changes — a push sends the
  revision it expects to replace, and the server returns the new revision.

The app encrypts the snapshot on the device before upload. It does not send your
passphrase or a plaintext ledger in this flow, and it does not save the passphrase; the
passphrase is used in memory for that operation. The vault ID is visible in the request
address, so do not put personal information in it.

The server can see the vault ID, your IP address, request timing, and the size of the
encrypted data. HTTPS protects the request contents in transit, but not all connection
details, such as timing or traffic size. Server-side storage, logging, retention, or
deletion depend on the server. Those practices for the preset endpoint must be confirmed
and disclosed by the project before this policy is published; they were not verified
for this revision.

### What syncs and what stays local

A vault holds a supported core ledger: income, bills, transactions, bill occurrences,
debts, the current balance, an update time, and internal record identifiers. It is
encrypted before it leaves the device.

A vault does **not** hold goals, assets, transaction rules, category budgets, accounts,
local settings, App Lock data, reminder or onboarding state, or the app's own vault
settings. Some of those records can be kept in a Standard Export or Encrypted Backup;
App Lock data and vault settings are not included in either export. A pull can remove
local-only records, so keep a backup if you need them.

**Pull and join replace the local core ledger.** They can delete or reset local-only
items. The app warns you, and asks for explicit confirmation when local-only content
would be lost. App Lock settings are preserved across a pull. A **Push** uploads the core
ledger only.

The app can stop an upload when it detects that the vault format cannot carry a record —
for example, inactive bills, statement-day or account-linked debt fields, and some
unsupported schedules. These checks are not a guarantee that every local detail is
represented in the vault. A successful push is not proof that all local data is there, so
keep a local backup before relying on sync.

## Exports and backups

Standard Export and Encrypted Backup are started by you and use Android's file picker.
Nothing is exported until you choose a destination.

- **Standard Export** is plaintext JSON. It can include your financial records, balances,
  budgets, debts, goals, assets, accounts, rules, settings, onboarding state, timestamps,
  and summary figures. It excludes App Lock data. Anyone who gets the file can read it.
- **Encrypted Backup** protects the same JSON with a password you choose. This revision
  uses a slow password-stretching step (PBKDF2-HMAC-SHA256), AES-256-GCM encryption, and a
  tamper-detection code (HMAC-SHA256). The app does not store the backup password and
  cannot recover the file if you lose it.
- Neither export includes the App Lock PIN, its salt or hash, other App Lock settings, or
  the app's vault settings (such as vault ID and server URL).
- Restoring a backup does not import App Lock data. The lock already set on the device
  stays in effect.

The app does not control copies made later by a file provider, cloud storage app, or
synchronization service. Delete exported files through the place where you saved them.
CSV imports read only the file you pick and stay on the device.

## What stays on the device

The app keeps its data in a private Room database and private app settings. Depending on
what you use, this includes:

- financial records, balances, budgets, debts, goals, assets, accounts, and rules;
- App Lock state: a salted PIN hash and lockout counters, not the PIN itself;
- reminder preferences and onboarding state;
- vault settings: vault ID, server URL, revision, and sync fingerprints. The passphrase
  is not stored there.

Reminders are generated locally. If App Lock is off, a bill reminder can show bill dates
and amounts; if App Lock is on, the text is reduced. The home-screen widget shows a
locked message when App Lock is enabled; otherwise it can show a safe-to-spend or
next-bill summary. No push service is used for these local surfaces.

## Erase and deletion boundaries

**Settings → Security & Privacy → Erase all local data** deletes the app's local ledger
tables and settings, including App Lock and reminder settings, and creates an empty
default account. It does **not** delete:

- backup or export files you saved through the file picker;
- a vault copy stored on a remote server;
- the app's separate vault settings — the saved vault ID, server URL, revision, and sync
  fingerprints remain until overwritten or until Android clears the app's data.

The app has no remote-delete operation. Removing a vault from a server requires that
server operator's tools or procedures. Erasing on the phone does not contact the vault.
If you later choose **Push**, it can replace the remote ledger with your current one,
including an empty ledger after erasure, if the vault revision still matches. Replacing
the contents is not the same as deleting the vault or its request records.

Erase all local data deletes database rows; it is not a guaranteed forensic wipe of the
device's storage.

## Accounts, tracking, and third parties

The app does not create an account or user profile. The app dependencies declared in this
revision do not include a third-party analytics, advertising, or crash-reporting SDK. The
vault client contacts the preset Web companion or the server address you enter.

If you use a third-party vault server or send an export to a third-party file provider,
that operator's terms and privacy practices apply. This policy does not claim a retention
period, security certification, or legal compliance status for those services.

## Policy status and limits

This policy describes the Android app in this repository. It is not a description of a
hosted service, a store listing, or a claim that a policy URL is publicly reachable. If
app behavior changes, this file must be updated.

This document does not assert compliance with any privacy framework and does not replace
the project owner's publication, store-disclosure, or legal review obligations.
