# Building RenaissanceLib

RenaissanceLib targets **two versions of TaC:Z**, built one at a time. A single jar can't support both — the
mod mixes into TaC:Z internals, and the unreleased beta changed the first-person render pipeline, so its bytecode
differs from the release. Build the variant you need:

| Command | TaC:Z target | Output jar |
|---|---|---|
| `./gradlew build` | **Stable** — the current CurseForge release (`8141310`) | `build/libs/renaissance_lib-<version>.jar` |
| `./gradlew build -Ptacz=beta` | **Beta** — the dev's unreleased jar | `build/libs/renaissance_lib-<version>-tacz-beta.jar` |

Both jars can coexist in `build/libs` (the beta one has a `-tacz-beta` suffix). Ship each to the matching TaC:Z.

## Beta prerequisite

The beta build compiles against a local TaC:Z jar that is **not committed** (it's an unreleased artifact). Put it
at:

```
libs/tacz-1.20.1-0825-015925-all.jar
```

If you receive a newer beta, drop it in `libs/` and update the `taczBetaJar` filename near the top of
`build.gradle`.

## How the two variants share code

Almost everything lives in `src/main` and is shared. Only the pieces that differ between the two TaC:Z bytecode
layouts are variant-specific, in `src/tacz_stable/java` and `src/tacz_beta/java` (added to the build by the
`-Ptacz` switch):

- **`compat/TaczCompat`** — thin wrappers over TaC:Z API calls whose signatures differ (e.g. the beta added an
  `AttachmentType` parameter to `renderAttachment`).
- **`compat/TaczDescriptors`** — mixin `method=` descriptors for TaC:Z methods that are *overloaded* in the beta
  (a name-only selector would be ambiguous), so each variant inlines the right overload's descriptor.

Uniquely-named methods whose signature merely changed are handled with plain name-only mixin selectors in the
shared mixins, so they need no variant copy.

## After a new beta drop

The beta is a moving target with no source. When the dev ships a newer beta, rebuild with `-Ptacz=beta` and watch
the Mixin annotation-processor warnings for `Cannot find target method` / `Unable to determine descriptor` — those
flag injection points that moved and need re-anchoring (usually a descriptor tweak in `TaczDescriptors` or a
switch to a name-only selector). Then test in-game.
