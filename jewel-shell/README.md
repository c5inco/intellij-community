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

From the command line, the same thing via jps-bootstrap. The `-D` options are not optional: without
`idea.platform.prefix` the platform falls back to `IdeaApplicationInfo.xml` and fails with
`EssentialPluginMissingException: Missing essential plugins: com.intellij.java, ...`, because it is
loading IDEA's product definition rather than this one.

```bash
./platform/jps-bootstrap/jps-bootstrap.sh \
  -Didea.platform.prefix=JewelShell \
  -Djava.awt.headless=false \
  -Didea.config.path="$PWD/config/jewelshell" \
  -Didea.system.path="$PWD/system/jewelshell" \
  . intellij.jewelShell.main com.intellij.idea.Main jewelShell [file ...]
```

Trailing arguments after `jewelShell` are opened in the shell's editors on startup, which is the
quickest way to exercise the real-editor path without going through the file chooser.

## Building distributions

Run `JewelShellInstallersBuildTarget.main()` (module `intellij.jewelShell.build`). It compiles the
product modules and produces archives with a bundled JetBrains Runtime under `out/`. Mac/Windows
signing steps are skipped; per-OS installer customizers (`.ico`/`.icns`/installer imagery) are left as
`null`/default and should be filled in with real brand assets — model them on
`communityWindowsCustomizer` & co. in `IdeaCommunityProperties.kt`.

## Shake-out — resolved

The scaffolding has now been built and launched from sources; the following was needed to get there,
and is already applied:

- **`intellij.platform.projectModel`** added to `intellij.jewelShellSample` — `LightEditServiceImpl`'s
  supertype `PersistentStateComponent` lives there, so the module did not compile without it.
- **`intellij.platform.buildScripts.downloader`** added (runtime) to `intellij.jewelShell.main` —
  jps-bootstrap always wraps the requested main class in
  `org.jetbrains.intellij.build.impl.BuildScriptLauncher`, which must be on the run module's classpath.
- **Two content modules added to `JewelShellPlugin.xml`.** As anticipated, the composed
  sets were not self-contained: `essential.minimal` is a smaller tier than the `ide.common`/`essential`
  tiers most products compose from, so `intellij.libraries.jspecify` (required by
  `intellij.libraries.compose.runtime.desktop`, and therefore by all of Jewel) and
  `intellij.platform.scopes` (required by `intellij.platform.searchEverywhere`) had to be listed
  explicitly. The startup log names these precisely, as `Module X is not enabled because dependency Y
  is not available` lines under `PluginManager - Plugin set resolution:`.
- **An `IdeGlassPaneImpl` installed on the shell window's root pane** (in the sample) — platform
  components hosted outside the standard IDE shell still look one up via `IdeGlassPaneUtil`.

Startup is now clean: `Loaded bundled plugins: IDEA CORE, Jewel Shell Sample`, with no plugin problems.

Still intentionally unaddressed:

- `essential-plugin` entries, file associations, and update channels are omitted.
- The distribution layout (`productLayout.addPlatformSpec`) may need additions the first time
  `JewelShellInstallersBuildTarget` runs — MPSProperties shows the typical adjustments. Only the
  run-from-sources path has been exercised so far.
