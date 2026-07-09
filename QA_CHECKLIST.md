# Monte Carlo Ledger — QA Checklist

---

## 1. Financial Precision

- [ ] Decimal/integer cents used for all currency — no floating point
- [ ] Rounding is deterministic (consistent tie-breaking rule)
- [ ] Negative amounts handled correctly
- [ ] Zero-balance edge case renders correctly
- [ ] Large-value precision maintained (no overflow or rounding errors)

**Pass criteria: 5/5**

---

## 2. Ledger Core

- [ ] Transaction entry works correctly
- [ ] Balance calculation matches expected values
- [ ] Reconciliation matches Room DB state
- [ ] Category filtering works
- [ ] Date range filtering works
- [ ] Search returns correct results

**Pass criteria: 6/6**

---

## 3. Monte Carlo Forecast

- [ ] 90-day forecast uses deterministic seed
- [ ] Repeatable results with same seed
- [ ] Configurable parameters work
- [ ] Visual chart renders correctly
- [ ] Edge cases handled (empty ledger)

**Pass criteria: 5/5**

---

## 4. Encrypted Backups

- [ ] AES-GCM export works
- [ ] Import restores data correctly
- [ ] Wrong password rejected
- [ ] Corrupted file rejected

**Pass criteria: 4/4**

---

## 5. UI

- [ ] Compose screens render correctly
- [ ] Navigation works
- [ ] State preserved on rotation
- [ ] Dark theme renders correctly
- [ ] Accessibility labels present
- [ ] No crashes

**Pass criteria: 6/6**

---

## 6. Permissions

- [ ] Storage access scoped to backups only
- [ ] No internet permission
- [ ] No unnecessary permissions

**Pass criteria: 3/3**

---

## 7. Go / No-Go Gate

**Ship when all items pass.**

- [ ] No floating-point currency in any file
- [ ] Room migration doesn't destroy data
- [ ] Forecast deterministic with same seed
