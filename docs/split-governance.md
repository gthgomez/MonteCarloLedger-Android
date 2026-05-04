# Split Governance

## Decision

Keep the product as one Android app for now.

There is no separate web app in this repository yet, so splitting the runtime today would add overhead without improving the user experience.

## Why This Is The Right Boundary

- The core workflows are shared and finance-focused.
- The Android app already has an adaptive shell and responsive dashboard.
- The domain math is platform-agnostic and should stay shared.
- A future web target will almost certainly need a different shell, routing, and input model.

## What Should Stay Shared If Web Is Added

- Recurrence math
- Forecast math
- Balance reconciliation rules
- Currency formatting helpers
- Theme tokens and semantic color roles
- Test fixtures and deterministic sample data

## What Should Become Platform-Specific

- Navigation shell
- Screen routing
- Layout composition
- Motion and transitions
- Accessibility implementation details
- Platform-native affordances like FAB placement, rails, drawers, or web keyboard shortcuts

## Split Rule

Split presentation layers first, not business logic.

If Android and web ever diverge enough that one platform has to mimic the other badly, then:

- keep the shared domain layer
- extract a shared design-token package
- give each platform its own shell and routing

## Good Signs For A Future Split

- Different release cadence
- Different primary navigation model
- Different ownership boundaries
- A shared shell starts forcing poor UX on one platform

## Bad Reasons To Split

- Wanting a second app for the sake of organization only
- Duplicating the glass look with no shared token layer
- Splitting before the web product actually exists

## Current Product Topology

- Android: source of truth for the live product
- Web: not yet implemented
- Shared logic: forecast, recurrence, reconciliation, and token definitions should remain portable

## Review Trigger

Revisit the split only when there is a concrete web implementation or a second platform with its own release and UX constraints.
