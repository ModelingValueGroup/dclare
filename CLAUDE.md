# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dclare** is a declarative rule engine for Java. Rules are enforced continuously and automatically — no listeners needed. The implementation is heavily multi-threaded while the API exposes no concurrency primitives.

- **Group:** `org.modelingvalue`, **Artifact:** `dclare`, **Version:** 5.1.0
- **Java:** 21
- **License:** LGPL-3.0
- **Dependencies:** `immutable-collections` and `mvg-json` (both from org.modelingvalue)

## Build Commands

```bash
./gradlew              # Runs default tasks: mvgCorrector, test, publish, mvgTagger
./gradlew test         # Run all tests
./gradlew test --tests "org.modelingvalue.dclare.test.DclareTests.source2target"  # Single test method
./gradlew test --tests "org.modelingvalue.dclare.test.DclareTests"               # Single test class
```

There is no separate lint command; the `mvgCorrector` task handles code corrections (copyright headers, formatting).

## Architecture

### Core Abstractions

The engine is built around a **transaction-based state machine** where immutable `State` snapshots evolve through transactions:

- **Universe** — Root mutable object; entry point for the entire model. Created via `Universe.of(...)`.
- **Mutable** — Base interface for all model objects. Establishes parent/child containment hierarchy.
- **MutableClass** — Metadata interface describing the features (observables, observers, derivers) of a Mutable type.

### Property System (Getable → Setable → Observed/Constant)

- **Getable<O,T>** — Base readable property with ID and default function.
- **Setable<O,T>** — Writable property. Supports opposites, scoping, orphan protection.
- **Observed<O,T>** — Tracked property that triggers observer re-evaluation on change.
- **Constant<O,T>** — Derived/cached value computed from a deriver function. Can be lazy or pushed.

### Rule System (Leaf → Action/Observer)

- **Action<O>** — Imperative operation triggered by state changes. Has priority and direction.
- **Observer<O>** — Declarative rule that re-executes when its dependencies change. The primary mechanism for maintaining consistency.
- **Derivation** — Lazy computation that evaluates on demand.

### Transaction Hierarchy

```
Transaction (abstract)
├── LeafTransaction (abstract)
│   ├── ActionTransaction
│   ├── ObserverTransaction
│   ├── DerivationTransaction / LazyDerivationTransaction / IdentityDerivationTransaction
│   ├── ReadOnlyTransaction
│   └── ImperativeTransaction
├── MutableTransaction
└── UniverseTransaction  ← orchestrates the overall execution loop
```

### Priority Scheduling

Actions/Observers have priorities (`Priority.one` through `Priority.six`) controlling execution order. Lower priorities execute first. `Priority.zero` represents the currently executing level.

### Modifiers

- **SetableModifier** / **CoreSetableModifier** — containment, mandatory, preserved, plumbing, durable, doNotMerge, symmetricOpposite
- **LeafModifier** / **CoreLeafModifier** — preserved, read
- **Direction** — Push (eager) vs Pull (lazy) evaluation

### Synchronization Module (`sync/`)

Enables multi-instance state synchronization via JSON deltas. Key classes: `DeltaAdaptor`, `SerialisationPool`, `UniverseSynchronizer`, `SocketSyncConnection`.

### OneShot

Abstract class for single-run model initialization. Methods annotated with `@OneShotAction` are discovered and executed in alphabetical order. Supports state caching.

## Naming Conventions

- `D_` prefix — Internal system properties on Mutable (e.g., `D_PARENT_CONTAINING`, `D_OBSERVERS`)
- `d*()` methods — Dclare-specific operations on Mutable (e.g., `dParent()`, `dChildren()`, `dDelete()`)
- Factory pattern — Most core types use `of(...)` static factory methods
- Property access — `.get(object)` to read, `.set(object, value)` to write within transaction context

## Configuration

`DclareConfig` supports system properties for debugging:

- `DEV_MODE` — Enable debug features
- `RUN_SEQUENTIAL` — Disable parallelism (useful for debugging)
- `CHECK_ORPHAN_STATE` — Validate orphan handling
- `TRACE_UNIVERSE`, `TRACE_MUTABLE`, `TRACE_ACTIONS`, `TRACE_MATCHING`, `TRACE_RIPPLE_OUT`, `TRACE_DERIVATION` — Various tracing flags
- `MAX_TOTAL_NR_OF_CHANGES`, `MAX_NR_OF_CHANGES`, `MAX_NR_OF_OBSERVED`, `MAX_NR_OF_OBSERVERS` — Safety limits

## Testing

Tests use JUnit 5. Test support classes are in `src/test/java/org/modelingvalue/dclare/test/support/` providing `TestUniverse`, `TestMutable`, `TestMutableClass`, and other helpers for constructing test models.

## File Headers

All source files include a copyright header block for Modeling Value Group B.V. The `mvgCorrector` gradle task maintains these automatically.

## Eclipse Integration

When `GRADLE_ECLIPSE=true` environment variable is set, the build uses `includeBuild` to substitute local checkouts of `immutable-collections` and `mvg-json` from sibling directories.
