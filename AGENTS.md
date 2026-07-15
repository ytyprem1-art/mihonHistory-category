# Mihon Mod Development Rules

## Upstream safety

- Keep all upstream Mihon files minimally modified.
- Existing Mihon files must only act as small integration bridges for mod features.
- Put mod-specific UI, state, ScreenModels, interactors, helpers, navigation logic, and business logic in separate mod-specific files and packages.
- Before editing an upstream file, first check whether the implementation can be placed in a new file.
- Prefer composable hosts, callbacks, adapters, extension functions, and existing public APIs.
- Do not modify SourceManager, core repositories, shared database models, or upstream domain logic unless absolutely necessary.
- Do not perform unrelated refactoring, renaming, reformatting, or import reordering in upstream files.
- Every upstream modification must be small, obvious, and easy to reapply after an upstream Mihon update.

## Package structure

Prefer mod-specific packages such as:

- eu.kanade.tachiyomi.ui.mod.linkedsource
- eu.kanade.tachiyomi.ui.mod.historycategory
- eu.kanade.tachiyomi.ui.mod.quicksourceswitcher
- eu.kanade.tachiyomi.ui.mod.updatewatch
- eu.kanade.tachiyomi.ui.mod.onboarding

## Implementation workflow

Before writing code:

1. Identify which files are upstream Mihon files.
2. Design the feature so most code lives in new mod-specific files.
3. State which upstream files require integration hooks.
4. Avoid database schema changes unless explicitly requested.

After writing code:

1. List every modified upstream Mihon file.
2. Explain why each modification was unavoidable.
3. Confirm that no unrelated upstream code was changed.
4. Run or recommend the relevant build check.
