# AGENTS.md — gokfre

## Project overview

gokfre is a keyboard-driven tiling window manager for Linux (X11 / Wayland) and
macOS. The user presses a global hotkey (default Super+K) to open a preview
window, then selects one or two tile keys to arrange the currently focused
window.

## Stack

- **Language:** Kotlin 2.0.20 on JVM 17+.
- **UI:** JetBrains Compose Desktop 1.7.3.
- **Build:** Gradle 9 with Kotlin DSL.
- **State:** Compose `mutableStateOf` / `SnapshotStateList`, persisted as JSON
  via kotlinx.serialization.
- **Native interop:** JNA for platform APIs; Rust `kbd-global` crate for Linux
  evdev global hotkeys.
- **D-Bus:** `dbus-java` (core + native unixsocket transport) is used on KDE to
  host the bridge KWin scripts report through.
- **Testing:** Kotest + JUnit 4 (Compose UI tests via `createComposeRule`),
  plus real-desktop E2E tests under `src/e2e/kotlin`.

## Project layout

```
src/main/kotlin/com/adrinand/gokfre/
  Main.kt                 # App bootstrap and single-instance socket.
  ui/                     # Compose screens, windows, tray, settings UI.
  viewmodel/              # SettingsViewModel + AppLayoutSettings.
  core/screen/            # WindowManager abstraction + OS backends.
  core/hotkey/            # Global hotkey abstraction + providers.
  core/persistence/       # SettingsRepository + serialized entities.
  core/comms/             # Unix-domain socket single-instance handshake.
  core/logging/           # AppLogging initialization.
lib/gokfre-hotkey/        # Rust library for Linux evdev hotkeys.
extensions/               # GNOME Shell extension.
```

## Build and test commands

```bash
# Run the app locally.
./gradlew run

# Full CI-quality verification (unit tests, detekt, ktlint, kover coverage).
# Does NOT run E2E tests.
./gradlew check

# Rust hotkey library tests (Linux only).
cd lib/gokfre-hotkey && cargo test

# Real-desktop end-to-end tests (skipped automatically in headless environments).
# On Wayland, arrangement tests need the GNOME/KDE helper extension/script
# enabled and xdotool installed to activate the target window.
./gradlew e2e

# Build packages for the host OS.
./gradlew package
```

## Agentic pre-PR workflow

This repo is configured for opencode:

- `AGENTS.md` and `REVIEW.md` are loaded as instructions.
- `/pre-pr` runs verification (`./gradlew check`, `cargo test`, `./gradlew e2e`),
  invokes the code and security reviewers, then commits, pushes, and ensures a
  draft PR exists.
- `/review` re-runs code and security review only.
- Review agents are read-only (`edit: deny`); the parent agent decides what to fix.

### Definition of done (mandatory)

An implementation task is not finished until the work is reviewed, committed,
pushed, and has a PR. Before reporting completion, the parent agent must:

1. Run `./gradlew check` (plus `cargo test` / `./gradlew e2e` when the change
   touches those areas).
2. Invoke both review subagents (`reviewer` and `security-reviewer`) with the
   Task tool on the current diff. Address `Important` findings, or report
   explicitly why they are out of scope.
3. Stage only the intended files and commit with a message matching the repo
   style.
4. Push the current branch (`git push -u origin HEAD`). Never finish with
   unpushed commits.
5. Ensure a PR exists. Always create it as a draft (`gh pr create --draft ...`);
   if a PR already exists, update it instead of opening a new one. Never mark a
   PR ready for review unless the user explicitly asks.
6. Report the PR URL, or the exact blocker that prevented pushing or opening it.

Review subagents are read-only, so the parent agent performs the commit, push,
and PR steps. If pushing or PR creation is impossible (no remote, missing auth),
say so explicitly instead of silently finishing.

## Conventions

- Package: `com.adrinand.gokfre.*`.
- Use `java.util.logging` via the project logger; never `println` in production.
- UI composables live in `ui.*`; business logic in `viewmodel.*` or `core.*`.
- OS-specific code is isolated behind interfaces (`WindowManager`,
  `GlobalHotkeyProvider`).
- Prefer immutable value objects for persistence; use `SnapshotStateList` only
  for Compose-bound mutable UI state.
- Keep composables focused; extract reusable components to small files.
- Keep comments minimal: short, and only for non-obvious *why* (compatibility
  quirks, security, ordering). Do not narrate code. Extended design notes belong
  in this file, not in code comments. Apply this to Kotlin, JS, and shell alike;
  when you add or change a comment, move any long explanation here instead.

## Wayland backends

Both backends run outside the app, so `core/screen/` talks to them over the
session bus. Design notes live here instead of in code comments.

- **GNOME (`GnomeWindowManager`)** needs the `gokfre@adrinand` extension.
  GNOME has no scripting D-Bus API: `org.gnome.Shell.Eval` returns
  `(false, '')` for external callers unless `unsafe-mode` is enabled (a
  deliberate security restriction), and `org.gnome.Shell.Introspect` is
  read-only. The extension runs in-process and exposes
  `org.gnome.Shell.Extensions.Gokfre`. GNOME disables extensions while the
  screen is locked.
- **KDE (`KdeWindowManager`)** needs nothing installed. KWin ships
  `org.kde.KWin.Scripting`, so each operation loads a short script, runs it, and
  unloads it. KWin scripts cannot own D-Bus names, so the app hosts
  `org.kde.gokfre.Bridge` (`KdeBridge`) and scripts report results with
  `callDBus` (`Report(token, json)`); `KdeScriptRunner` matches replies by token.
  The script is `src/main/resources/kde/gokfre-kde.js`; `KdeScriptRenderer`
  renders it per operation by substituting `__GOKFRE_*__` placeholders. Every
  script gets a fresh JS context, so the file stays self-contained. CI starts
  KWin with `--virtual --xwayland` (virtual backend, QPainter compositing), so
  no GPU or logind session is needed; XWayland only exposes `-displayfd`, so
  CI finds its display by probing `:0`–`:9`.
- **KWin compatibility:** Plasma 5 exposes `activeClient`/`clientList`, Plasma 6
  `activeWindow`/`windowList` (support both). Scripts live at `/<id>` on 5.27
  and `/Scripting/Script<id>` on newer builds. `frameGeometry` is read-write;
  `internalId` exists on 5.27+; maximize uses `setMaximize(bool, bool)`, and
  `maximizeMode` is read-only (3 = full).

## Things that are easy to break

- **OS window management:** Changes in `core/screen/` can break X11, Wayland,
  or macOS behavior. Always test on the target OS. E2E arrangement tests run
  on X11 and on Wayland GNOME/KDE when the helper backend is enabled (GNOME
  needs the extension installed; KDE uses KWin scripting and needs nothing
  installed). GNOME disables extensions while the screen is locked, which makes
  the arrangement test fail with a clear message.
- **Global hotkeys:** Linux evdev needs `/dev/input/event*` and `/dev/uinput`
  access. The Rust native lib must be rebuilt after changes.
- **Single-instance socket:** `AppSocketComms` uses a fixed Unix socket path
  under `/tmp`. Tests use isolated settings paths but share the socket logic.
- **Settings persistence:** `SettingsRepository` auto-saves with a 500ms
  debounce. Closing the app too fast in tests can lose changes.
- **Tray icon:** Linux uses dorkbox/SystemTray with AppIndicator. Can fail
  silently on headless or unsupported desktops.

## Non-goals

- Do not add a web UI or remote control API.
- Do not support Windows in this repo.
- Do not replace the OS compositor; gokfre is a helper, not a WM server.

## When editing this file

Update this file when you change the stack, build commands, or project
structure. Reviewers should flag changes that make a line here out of date.
