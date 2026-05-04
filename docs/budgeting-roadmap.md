# Budgeting App Roadmap

This roadmap turns the competitor comparison into concrete product work for MonteCarlo Ledger.

It is written for implementation, not strategy theater:

- every item is tied to a current app gap
- every item includes the user problem it solves
- every item is ordered by impact and ease of shipping
- every item is biased toward the fastest improvement in first-time user success

## What Our App Is Today

The current product is best described as a manual-first forecasting ledger.

What that means in practice:

- users enter income, bills, and transactions by hand
- the dashboard explains bank balance, app balance, starting balance, and safe-to-spend
- the app has onboarding milestones and reconciliation
- forecast math is a core feature, not an afterthought
- there is no bank connection, transaction import, shared budget, or account aggregation layer yet

That is a valid product direction.

The problem is not the concept. The problem is friction.

## Competitor Pattern Summary

The biggest budgeting apps cluster into two product styles:

### 1. Clarity-first apps

These apps win by making the budgeting model easy to understand:

- [YNAB features](https://www.ynab.com/features)
- [Goodbudget how it works](https://goodbudget.com/how-it-works/)
- [EveryDollar features](https://www.ramseysolutions.com/money/everydollar/features)

Common pattern:

- one simple budget model
- obvious next action
- less jargon
- fast onboarding
- strong habit formation

### 2. Automation-first apps

These apps win by removing manual work:

- [Monarch Money](https://www.monarchmoney.com/)
- [PocketGuard](https://pocketguard.com/)
- [Rocket Money budget feature](https://www.rocketmoney.com/feature/create-a-budget)
- [Quicken Simplifi dashboard](https://support.simplifi.quicken.com/en/articles/3357180-getting-to-know-your-dashboard)
- [Simplifi Bill Connect](https://support.simplifi.quicken.com/en/articles/4993562-using-bill-connect-to-track-your-bills-on-the-mobile-app)
- [Copilot iPad support](https://help.copilot.money/en/articles/10003978-copilot-money-for-ipad)

Common pattern:

- bank or account sync
- recurring transaction detection
- alerts and notifications
- bills/calendar view
- overview dashboard that reduces navigation
- polish that makes the app feel instantly trustworthy

## Product Positioning Decision

We should not try to copy every competitor at once.

The best near-term position for MonteCarlo Ledger is:

- manual-first, but much faster
- forecast-first, but plain language
- simple enough for beginners
- powerful enough to earn trust from people who care about cash flow

That means:

- borrow the clarity of YNAB and Goodbudget
- borrow the automation roadmap of Monarch and Simplifi
- do not add heavy sync or multi-account features before the basic flows feel effortless

## Priority Roadmap

### P0: Fix the core onboarding friction

These are the highest priority because they directly affect whether a new user gets to value.

#### 1. Make bill setup much shorter

Current issue:

- bill entry still asks for too many decisions too early
- recurrence, due day vs due date, and auto-pay all show up in the main flow

Implement next:

- default bill frequency to `Monthly`
- hide due date input until the chosen recurrence actually requires it
- keep auto-pay behind an advanced toggle
- allow the bill to save with only:
  - name
  - amount
  - recurrence
  - due day or due date only when needed

Why this matters:

- this is the biggest first-run friction point in the app
- it is the closest match to how users actually think: "I have a bill, here is the amount, remind me when it is due"

Recommended files:

- `app/src/main/java/com/example/app/ui/AddPaymentScreen.kt`
- `app/src/main/java/com/example/app/processing/PaymentSchedule.kt`
- `app/src/main/java/com/example/app/ui/ScheduleDatePickerField.kt`

Definition of done:

- a first-time user can add a bill without understanding the recurrence engine
- monthly bills do not force a due-date picker unless necessary
- auto-pay is discoverable but not in the primary mental load

#### 2. Make income setup feel like a quick add, not a finance form

Current issue:

- the income screen is better than the bill screen, but it still asks for more detail than needed to get started

Implement next:

- keep the required fields to:
  - income source
  - amount
  - frequency
  - last payday
- keep expected/usual amount optional and hidden by default
- keep the date picker, but make the next step obvious after the user types the source and amount

Why this matters:

- users should be able to add a paycheck in under a minute
- the app should feel helpful immediately, not like a payroll worksheet

Recommended files:

- `app/src/main/java/com/example/app/ui/AddIncomeScreen.kt`
- `app/src/main/java/com/example/app/data/IncomeEntity.kt`
- `app/src/main/java/com/example/app/data/LedgerRepository.kt`

Definition of done:

- first paycheck can be entered with minimal decisions
- "optional" remains truly optional
- the screen reads like a simple form, not a setup wizard

#### 3. Reduce the number of concepts shown on the dashboard

Current issue:

- the dashboard still explains balance, app balance, starting balance, forecast, and onboarding at the same time
- that is useful for power users, but dense for beginners

Implement next:

- keep one obvious primary action at a time
- show only the most relevant next step for the current onboarding stage
- keep the forecast explanation, but compress it into a single sentence
- remove repeated explanations that appear in both the dashboard and the analysis page

Why this matters:

- the app should answer "what do I do next?" immediately
- the dashboard should feel like the home screen, not a glossary

Recommended files:

- `app/src/main/java/com/example/app/ui/DashboardScreen.kt`
- `app/src/main/java/com/example/app/ui/AppView.kt`
- `app/src/main/java/com/example/app/data/OnboardingProgress.kt`

Definition of done:

- the dashboard has one clear next action
- the jargon is hidden behind helper text or advanced detail
- the user can read the page without having to mentally translate every label

### P1: Remove manual work where users feel it most

These features close the gap with the automation-first competitors without turning the product into a heavy fintech suite.

#### 4. Add transaction import before full bank sync

Current issue:

- all data is entered manually
- that is acceptable for early power users, but it is a barrier for everyone else

Implement next:

- add CSV import for transactions and bills
- map common columns to app entities
- let users review and confirm imports before saving

Why this matters:

- CSV import is the fastest bridge between manual entry and full bank sync
- it gives us automation value without the product risk of a live account connection

Recommended files:

- `app/src/main/java/com/example/app/data/LedgerRepository.kt`
- `app/src/main/java/com/example/app/data/TransactionDao.kt`
- a new import screen under `app/src/main/java/com/example/app/ui/`

Definition of done:

- a user can get useful data into the app without entering every item by hand
- import is safe, previewed, and reversible

#### 5. Add a bill calendar or timeline view

Current issue:

- due dates exist, but the experience is still split across dashboard cards and list views

Implement next:

- add a single calendar/timeline surface for upcoming bills
- show due date, amount, and paid/unpaid state
- let users tap a bill to edit or mark it paid

Why this matters:

- this is a common expectation in Simplifi, Monarch-style dashboards, and other modern budgeting apps
- it makes the app feel more proactive

Recommended files:

- `app/src/main/java/com/example/app/ui/PaymentListScreen.kt`
- `app/src/main/java/com/example/app/ui/DashboardScreen.kt`
- `app/src/main/java/com/example/app/processing/TimelineService.kt`

Definition of done:

- a user can see the next two weeks or next month of obligations at a glance
- upcoming bills are easier to trust than a list buried in another screen

#### 6. Add smarter transaction matching

Current issue:

- bill linking is already there, but it is still something the user has to consciously manage

Implement next:

- suggest matches when the transaction amount and payment amount line up
- preselect the most likely unpaid bill
- let users override the suggestion

Why this matters:

- this reduces the repetitive bookkeeping that makes manual apps tiring
- it gets us closer to the "it just knows" feel of the better apps

Recommended files:

- `app/src/main/java/com/example/app/ui/AddTransactionScreen.kt`
- `app/src/main/java/com/example/app/data/LedgerRepository.kt`
- `app/src/main/java/com/example/app/domain/DomainRules.kt`

Definition of done:

- expense entry can suggest the correct bill without making the user search for it
- the app still stays in control of validation

#### 7. Add backup/export

Current issue:

- users need confidence that their manual work is safe

Implement next:

- add export to CSV or JSON
- include income, payments, transactions, and settings
- make restore possible from the exported file

Why this matters:

- all serious budgeting apps give users confidence that their data is portable
- this is especially important before adding any new automation or sync layer

Recommended files:

- `app/src/main/java/com/example/app/data/AppDatabase.kt`
- `app/src/main/java/com/example/app/data/LedgerRepository.kt`
- a new settings/export screen

Definition of done:

- data can be backed up locally
- exports are simple enough for non-technical users to understand

### P2: Add trust-building features that make the app feel complete

These features are important, but they should come after the core flows are easier.

#### 8. Add optional account sync

Current issue:

- the app has no external account aggregation yet

Implement later:

- choose one aggregation approach
- keep it optional
- do not make account sync required for basic usage

Why this matters:

- this is the main feature that closes the gap with Monarch, PocketGuard, Rocket Money, and Simplifi
- it is also the most operationally expensive feature

Recommended rule:

- do not add live bank sync until the manual flows are already excellent

#### 9. Add goals and watchlists

Current issue:

- the app forecasts cash flow, but it does not yet help users optimize toward a named goal

Implement later:

- goal-based savings targets
- watchlists for bills, balances, or categories
- threshold alerts

Why this matters:

- YNAB-style users want intention, not only observation
- this creates retention once the core flows are stable

#### 10. Add collaboration or shared budgets

Current issue:

- the app appears single-user only

Implement later:

- shared household budget support
- read-only or shared access modes
- role-based edits if needed

Why this matters:

- this is a strong differentiator for family or couple budgeting
- but it only matters after the single-user experience is already easy

### P3: Cross-platform polish and growth features

These are good future bets, but they are not the first product hill to climb.

#### 11. Add widgets and quick actions

Why:

- helps daily use
- matches expectations from mature mobile finance apps

#### 12. Add notification and reminder tuning

Why:

- users should be nudged before bills are due, not after

#### 13. Add a web companion later

Why:

- useful once the product model stabilizes
- not necessary for the next few UX wins

## Implementation Order

If the team wants the highest-value sequence, do this:

1. Simplify bill setup
2. Simplify income setup
3. Reduce dashboard jargon and duplicate explanation
4. Add CSV import
5. Add bill calendar/timeline
6. Add transaction matching
7. Add export/backup
8. Evaluate live sync
9. Add goals/watchlists
10. Add collaboration

## Suggested Success Metrics

Track these before and after the work:

- time to first paycheck added
- time to first bill added
- percentage of users who complete setup
- percentage of users who reach a forecast view
- number of users who add a second bill
- number of manual edits needed per imported item
- support questions about "how do I add a bill"

## Immediate Coding Targets

If the next sprint starts now, the best files to touch first are:

- `app/src/main/java/com/example/app/ui/AddPaymentScreen.kt`
- `app/src/main/java/com/example/app/ui/AddIncomeScreen.kt`
- `app/src/main/java/com/example/app/ui/DashboardScreen.kt`
- `app/src/main/java/com/example/app/ui/AppView.kt`
- `app/src/main/java/com/example/app/ui/AddTransactionScreen.kt`
- `app/src/main/java/com/example/app/data/LedgerRepository.kt`

## Final Recommendation

Do not try to become "every budgeting app" all at once.

Our sharpest path is:

- simpler setup than the manual budgeting apps
- clearer forecasting than the automation-first apps
- better plain-language UX than what we have today

If we ship the P0 and P1 items above, the app will feel dramatically more usable without changing its core identity.
