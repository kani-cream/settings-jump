# Phase 0 — Technical Validation Findings

- Status: In progress
- Target: design.md v0.6, PoC Gate 1–3
- Measured on: IntelliJ IDEA Community 2024.2.5, headless platform test composition
  (BasePlatformTestCase; bundled plugin subset — full-IDE composition still to be measured via runIde)

## How to run

```bash
cd poc
./gradlew test          # Gate 1/2 report + Gate 3 preflight checks (headless)
./gradlew runIde        # Manual: Tools > Settings Jump PoC > (Gate 1/2 dump, Gate 3 open/fail-closed)
```

The headless report is written to `poc/build/gate-reports/gate12-headless.md`.

## Gate 1 — Eligible Page discovery (headless composition)

| Metric | Value |
|---|---|
| Total pages observed | 119 (APPLICATION 54 / PROJECT 65, nested 18) |
| Eligible (strict, page-level) | 100 (84.0%) |
| Collection time (metadata only) | 6 ms |

Ineligibility reasons (a page may have several):

| Reason | Count |
|---|---|
| NO_STABLE_ID | 8 |
| NO_DISPLAY_METADATA | 3 |
| DYNAMIC_CHILDREN | 8 |
| CHILDREN_EP_NAME | 1 |

- Pages ineligible ONLY due to dynamic/childrenEPName flags: 9 — page-level identity/display
  metadata quality is high (identity failures are only 8+3).
- Display name sources: EXPLICIT_BUNDLE_KEY 109 / PLUGIN_DEFAULT_BUNDLE_KEY 7 / NONE 3.
  Almost everything resolves via key+bundle, confirming design.md 4.1 (default-bundle
  resolution must be implemented, otherwise ~92% of pages would have no display name).
- Design target pages found eligible: Keymap (`preferences.keymap`), Appearance
  (`preferences.lookFeel`), HTTP Proxy (`http.proxy`), Plugins (`preferences.pluginManager`),
  Git (`vcs.Git`), Gradle (`reference.settingsdialog.project.gradle`).

## Gate 2 — Coverage observations (headless composition)

| Observation | Count |
|---|---|
| CONTEXTUAL (provider or nonDefaultProject) | 37 / 119 |
| dynamic=true | 8 |
| childrenEPName declared | 1 (`preferences.editor` -> `com.intellij.editorOptionsProvider`) |

### Finding A (design impact): key everyday pages are NOT plain static children

- **Code Style** (`preferences.sourceCode`) and **Colors & Fonts** are `dynamic=true`
  → Non-eligible under current design. The *parent* page itself is statically declared;
  only its children are dynamic. Treating the parent as eligible-but-childless may be
  the right refinement (design 8.2 currently marks the whole page Non-eligible).
- **Code Completion** (`editor.preferences.completion`) is supplied via
  `childrenEPName=com.intellij.editorOptionsProvider` — one of the design's Gate 1
  target pages is unreachable under the v1.0 "childrenEPName → Non-eligible" rule.

### Finding B (design opportunity): childrenEPName IS metadata-enumerable

Probing `com.intellij.editorOptionsProvider` via public `ExtensionPointName` enumeration
returned 8 static `ConfigurableEP` declarations without instantiating anything —
including Code Completion (eligible-quality metadata). The design's v1.x extension
("if the referenced EP can be safely enumerated from metadata alone") appears feasible;
Gate 2 full-IDE measurement should confirm before revisiting design 8.2.

## Gate 3 — Stable Navigation (headless portion)

- Missing ID → preflight returns Unavailable; `ShowSettingsUtil` is never called. PASS.
- 20 static eligible pages → preflight (`isAvailable` + `canCreateConfigurable`) passes. PASS.
- `findEp` metadata lookup: ~0.0 ms average over 119 pages; full collection 6 ms. PASS.
- **Still open (requires runIde, manual):** actual dialog opening via the public predicate
  overload — correctness (right page selected), latency vs. plain Settings open, and
  no side effects from predicate traversal. Use `Tools > Settings Jump PoC`.

## Open items before Gate GO/NO-GO

1. Run the Gate 1/2 dump inside a real IDE (runIde) for the full plugin composition;
   headless numbers undercount (e.g. no third-party plugins).
2. Manual Gate 3: open Keymap / Gradle / Git / a CONTEXTUAL page / a missing ID from the
   Tools menu; record timings and verify the correct page is selected.
3. ~~Decide the design refinement raised by Finding B (childrenEPName enumeration)~~
   — RESOLVED: design.md v0.8 supports childrenEPName; the production index and
   navigation traverse referenced EPs (Code Completion is searchable and openable).
4. Finding A (parents of dynamic children, e.g. Code Style / Colors & Fonts) is
   still excluded per design — revisit if users miss those pages.
