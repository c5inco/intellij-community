# JewelShell — standalone product definition

Product scaffolding that turns the [Jewel Shell sample](../plugins/jewel-shell-sample/README.md) into a
**standalone application built on the IntelliJ Platform core** — its own launcher, branding, and
config/cache directories, installable and runnable **side by side with IntelliJ IDEA**, the same way
Android Studio is a separate product built from this platform.

## Layout

| Directory | Module | Purpose |
|-----------|--------|---------|
| `customization/` | `intellij.jewelShell.customization` | Product identity: `JewelShellApplicationInfo.xml` (name, version, icons, `script="jewelshell"`), the hand-written core plugin descriptor `META-INF/JewelShellPlugin.xml`, and `JewelShellMain` — a thin entry point that always boots through the `jewelShell` `ApplicationStarter`, so a double-click launch opens the Jewel shell with no command-line arguments |
| `main/` | `intellij.jewelShell.main` | Run-from-sources classpath aggregator: platform (`intellij.platform.monolith.main`) + the sample plugin + TextMate (cheap syntax highlighting for most languages) — **no Java/Kotlin/Groovy/Gradle plugins**, so the compile scope is a fraction of full IDEA |
| `build/` | `intellij.jewelShell.build` | `JewelShellProperties` (the product definition, modeled on `MPSProperties`/`PyCharmCommunityProperties`) and `JewelShellInstallersBuildTarget` (builds distributions with a bundled JBR, which Jewel requires) |

## Key product choices

- **`platformPrefix = "JewelShell"`** → the platform loads `META-INF/JewelShellPlugin.xml` as the core
  plugin descriptor. It is hand-written (this product opts out of the Product DSL generator, like MPS —
  `getProductContentDescriptor()` returns `null`) and composes the committed generated module-set XMLs:
  `PlatformLangPlugin.xml` + `intellij.moduleSets.essential.minimal.xml` (the documented
  "lightweight IDEs with basic editing" tier, used by Gateway) + `intellij.moduleSets.compose.xml`
  (Jewel + Compose runtime).
- **`getSystemSelector()` → `JewelShell<version>`** — own config/caches, zero interference with other
  installed IDEs; both dev run configs also use separate `../config/jewelshell` / `../system/jewelshell`.
- **`mainClassName = com.intellij.jewelShell.JewelShellMain`** — prepends the `jewelShell` command and
  delegates to `com.intellij.idea.Main`, replacing LightEdit's hardcoded prefix check in
  `ApplicationLoader.createDefaultAppStarter` with a mechanism the product owns.
- **Bundled plugins**: the Jewel Shell sample plugin + TextMate + the platform defaults; no language IDE
  plugins.

## Running from sources

Use the **JewelShell** run configuration. Compared to the *Jewel Shell Sample* config (which launches
full IDEA with the `jewelShell` argument), this one builds only the closure of
`intellij.jewelShell.main` — the platform without the language plugins — and uses the `JewelShell`
platform prefix, own config/system dirs.

## Building distributions

Run `JewelShellInstallersBuildTarget.main()` (module `intellij.jewelShell.build`). It compiles the
product modules and produces archives with a bundled JetBrains Runtime under `out/`. Mac/Windows
signing steps are skipped; per-OS installer customizers (`.ico`/`.icns`/installer imagery) are left as
`null`/default and should be filled in with real brand assets — model them on
`communityWindowsCustomizer` & co. in `IdeaCommunityProperties.kt`.

## Expected shake-out

This scaffolding is structurally faithful to the in-repo product templates but has not been built in CI:

- The hand-written `JewelShellPlugin.xml` may need a few more content modules or aliases once the
  platform validates it at startup (missing `required` content modules are named in the error).
- `essential-plugin` entries, file associations, and update channels are intentionally omitted.
- The distribution layout (`productLayout.addPlatformSpec`) may need additions the first time
  `JewelShellInstallersBuildTarget` runs — MPSProperties shows the typical adjustments.
