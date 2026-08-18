# Jewel Shell Sample

A working sample of running the IntelliJ Platform under a **completely custom UI shell rendered with
[Jewel](../../platform/jewel/README.md)** (Compose for Desktop), while the standard IDE shell —
`IdeFrameImpl`, `IdeRootPane`, the main toolbar, tool window panes, and `EditorsSplitters` — is never
instantiated.

The approach is modeled on the **LightEdit** implementation
(`IdeStarter.StandaloneLightEditStarter`, `LightEditServiceImpl`), which is the in-tree precedent for
"same platform core, different shell".

## How it works

| Step | Mechanism | LightEdit precedent |
|------|-----------|---------------------|
| Boot the platform without a frame | [`JewelShellStarter`](src/JewelShellStarter.kt) is registered on the public `com.intellij.appStarter` EP and overrides `IdeStarter.openProjectIfNeeded`, so the welcome frame and `IdeProjectFrameAllocator` never run | `StandaloneLightEditStarter` does exactly this |
| Get a `Project` without the standard frame | Reuses the LightEdit project container via `LightEditServiceImpl.getOrCreateProject()` — a lightweight `Project` constructed directly, bypassing `ProjectManager.openProject` and frame allocation | `LightEditProjectManager.getOrCreateProject()` |
| Custom shell | [`JewelShellWindow`](src/JewelShellWindow.kt) is a plain `JFrame`; its chrome (header toolbar, file sidebar, empty state) is 100% Jewel via `org.jetbrains.jewel.bridge.compose { }` | `LightEditFrameWrapper` (Swing) — replaced here with Jewel |
| Editors | Real platform editors (`EditorFactoryImpl.createMainEditor` + `EditorHighlighterFactory`) hosted as Swing islands in a `CardLayout`; documents, VFS, lexer highlighting all come from the platform | LightEdit also uses real editors |

Compose for Desktop dispatches on the AWT EDT — the same thread as the rest of the IDE — so the Compose
snapshot state (`mutableStateListOf`, `mutableStateOf`) is mutated directly from Swing callbacks and
Compose click handlers call Swing code directly, with no thread hops.

### Keeping the slow work off the EDT

Sharing the EDT with Compose does *not* mean opening a file can happen there. `JewelShellWindow.openFile`
is a suspend function that follows the same sequence as the platform's own
`PsiAwareTextEditorProvider.createFileEditor`:

| Step | Thread | Why |
|------|--------|-----|
| `LocalFileSystem.refreshAndFindFileByNioFile` | `Dispatchers.IO` (in `JewelShellStarter`) | walks and may refresh the persistent VFS |
| `readAction { FileDocumentManager.getDocument(file) }` | background, read lock | reads file contents through the VFS |
| `readActionBlocking { createEditorHighlighter(file, scheme, project) }`, then `highlighter.setText(...)` | background, read lock | file-type resolution can block on e.g. TextMate bundle init; priming the highlighter keeps lexing off the EDT |
| `EditorFactoryImpl.createMainEditor(...)` | `Dispatchers.EDT` + `writeIntentReadAction` | `EditorImpl`'s constructor asserts it is on the EDT |

Doing any of the first three on the EDT trips `SlowOperations.assertSlowOperationsAreAllowed`.

`createMainEditor` is used rather than the public `EditorFactory.createEditor(document, project, file,
isViewer)` because it is the only entry point that accepts a **pre-built** highlighter — the public
overload builds one itself on the calling thread, which would put the work back on the EDT. It is
`@ApiStatus.Internal`, the same caveat as the `LightEditServiceImpl` cast below.

## Running

Use the **Jewel Shell Sample** run configuration (a copy of the community **IDEA** run configuration with
the `jewelShell` program argument), or add `jewelShell [file ...]` as program arguments to any IDE launch.
Because the command is not in `WellKnownCommands`, it boots in full GUI (non-headless) mode, and
`ApplicationLoader` selects this starter by its id instead of the default `IdeStarter`.

The window opens with an empty state; use **Open File…** to load any file from disk. Files open with
platform lexer syntax highlighting; **Save All** writes changes back through `FileDocumentManager`.

## Scope and known limitations

This is a deliberately small spike, not a product:

- **Editors are created directly via `EditorFactory`**, not through `FileEditorManager`/`TextEditor`, so
  daemon inspections and code completion popups are not fully wired; lexer syntax highlighting works.
  A fuller version would register `TextEditor` instances or a custom `FileEditorManager` the way
  `LightEditFileEditorManagerImpl` does — which would also get `AsyncEditorLoader` (the platform's
  mechanism for deferring post-load work) for free; it is not usable from `EditorFactory`-based code,
  since it requires a `TextEditorProvider` and a `TextEditorImpl`.
- The standard `WindowManager` service still runs (registered but idle); this sample never asks it for
  a frame. Code that unconditionally expects an `IdeFrame` for the project would get `null`, as in
  LightEdit/headless modes.
- `LightEditServiceImpl` is `@ApiStatus.Internal`; the cast in `JewelShellStarter` is fine for an
  in-repo sample but is not stable API. A real product would construct its own project container
  (as `LightEditProjectImpl` does) or override `ProjectManager.createFrameAllocator`.
- Closing the window saves all documents and exits the application.

## Where a real product would go next

- Define a dedicated product (`ProductProperties` + `corePlatform()`/`essentialMinimal()` module sets)
  instead of piggybacking on IDEA, and add the Jewel standalone/decorated-window modules for a fully
  Compose-drawn title bar. **See [`jewel-shell/`](../../jewel-shell/README.md) for exactly this** — a
  standalone JewelShell product definition that bundles this plugin and boots straight into the Jewel
  shell.
- Replace `ToolWindowManager` (the one shell service registered `open="true"`) to route tool-window
  content into Jewel surfaces.
- Host editors via a custom `FileEditorManagerEx` so the daemon, completion, and navigation are fully
  functional inside the custom shell.
