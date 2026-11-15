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
./gradlew run

./gradlew check

cd lib/gokfre-hotkey && cargo test

./gradlew e2e

./gradlew package
```

`./gradlew check` runs unit tests, detekt, ktlint, and kover; it does not run
E2E. `./gradlew e2e` needs a real desktop (skipped when headless); on Wayland it
needs the GNOME extension or KWin scripting, and `xdotool` activates the target
window. `cargo test` is Linux-only. Install the GNOME extension with
`./gradlew installGnomeExtension`.

## Agentic pre-PR workflow

This repo is configured for opencode:

- `AGENTS.md` and `REVIEW.md` are loaded as instructions.
- `/pre-pr` runs verification (`./gradlew check`, `cargo test`, `./gradlew e2e`),
  invokes the code and security reviewers, commits, reports the pre-push summary,
  then pushes and ensures a draft PR exists.
- `/review` re-runs code and security review only.
- Review agents are read-only (`edit: deny`); the parent agent decides what to fix.

### Definition of done (mandatory)

An implementation task is not finished until the work is reviewed, committed,
pushed, and has a PR. This is unconditional: implementation agents must commit,
push, and ensure a draft PR exists even when the user did not explicitly ask, so
the branch is available for review and local testing. Before reporting
completion, the parent agent must:

1. Run `./gradlew check` (plus `cargo test` / `./gradlew e2e` when the change
   touches those areas).
2. Invoke both review subagents (`reviewer` and `security-reviewer`) with the
   Task tool on the current diff. Address `Important` findings, or report
   explicitly why they are out of scope.
3. Stage only the intended files and commit with a message matching the repo
   style.
4. Before pushing, summarize the changes, state the assumptions made, and list
   what the human still needs to do (see below).
5. Push the current branch (`git push -u origin HEAD`). Never finish with
   unpushed commits.
6. Ensure a PR exists. Always create it as a draft (`gh pr create --draft ...`);
   if a PR already exists, update it instead of opening a new one. Never mark a
   PR ready for review unless the user explicitly asks.
7. Report the PR URL, or the exact blocker that prevented pushing or opening it.

### Pre-push report

Every implementation task ends with a short report containing:

- **Changes:** what was implemented and which files/areas were touched.
- **Assumptions:** environment or backend assumptions, tests that could not run,
  and any behavior deliberately left out of scope.
- **Human follow-ups:** actions only the user can take (e.g. log out/in to reload
  the GNOME extension, install a package, grant device permissions, verify on
  real hardware, or mark the PR ready).

Review subagents are read-only, so the parent agent performs the commit, push,
and PR steps. If pushing or PR creation is impossible (no remote, missing auth),
say so explicitly instead of silently finishing.

Implementation agents must always push. Never leave implementation work only in
the local working tree; pushing is part of the task, not an optional follow-up.

## Conventions

- Package: `com.adrinand.gokfre.*`.
- Use `java.util.logging` via the project logger; never `println` in production.
- UI composables live in `ui.*`; business logic in `viewmodel.*` or `core.*`.
- OS-specific code is isolated behind interfaces (`WindowManager`,
  `GlobalHotkeyProvider`).
- Prefer immutable value objects for persistence; use `SnapshotStateList` only
  for Compose-bound mutable UI state.
- Keep composables focused; extract reusable components to small files.
- Keep comments to 1–2 lines and only for non-obvious *why* (compatibility
  quirks, security, ordering). Do not narrate code. Longer explanations belong
  in this file, not in code comments; move them here when you add or change a
  comment. Apply this to Kotlin, JS, and shell alike.

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
  script gets a fresh JS context, so the file stays self-contained. KWin owns
  the X11 listening socket and spawns Xwayland lazily on the first X11 client
  connection, so CI probes `/tmp/.X11-unix/X0`–`X9` with `xdpyinfo` instead of
  waiting for an `Xwayland` process; CI starts KWin with `--virtual --xwayland`
  (virtual backend, QPainter compositing), so no GPU or logind session is
  needed.
- **KWin compatibility:** Plasma 5 exposes `activeClient`/`clientList`, Plasma 6
  `activeWindow`/`windowList` (support both). Scripts live at `/<id>` on 5.27
  and `/Scripting/Script<id>` on newer builds. `frameGeometry` is read-write;
  `internalId` exists on 5.27+; maximize uses `setMaximize(bool, bool)`, and
  `maximizeMode` is read-only (3 = full).

### Preview focus and work areas

- **Focus layers:** the preview needs compositor focus, AWT window focus, and
  Compose focus. Make it focusable before mapping (`Window(focusable = true)`,
  `alwaysOnTop = visible`), focus it right after `isShowing`, then re-assert
  focus after placement. Placement/maximize must never gate keyboard focus.
- On GNOME, `FocusPreview` uses `Main.activateWindow` with a roundtrip timestamp
  and returns `preview.has_focus()`; the app retries for ~500 ms before falling
  back to AWT. Shell-side activation is required because `arrange()` just
  activated the target app, so Mutter's focus-stealing prevention blocks AWT
  activation; do not call AWT `toFront()`/`requestFocus()` after it.
- **Coordinate spaces:** AWT/XWayland can report native pixels when JVM UI scaling
  is off (common in E2E), while GNOME and KWin compositor APIs use logical
  coordinates. `WindowManager.getWorkArea(window, bounds)` must return the
  backend's own work area; `awtWorkAreaForWindowBounds` is AWT-space only. KDE
  currently inherits the AWT fallback and needs a backend override on HiDPI.
- On GNOME, place the preview shell-side (atomic unmaximize + move + maximize);
  client-side moves of a maximized window race the WM and can leave the preview
  maximized on the previous monitor. Capture the logical work area in the same
  `CaptureActiveWindow` call to avoid an extra blocking `gdbus` call. Older
  extensions without logical work areas use client-side AWT placement instead.
- Only record the preview's monitor after placement succeeds; a failed move must
  be retried on the next open.
- **Extension reloads:** GNOME Shell 45+ caches `extension.js`; disabling/enabling
  or `ReloadExtension` does not reload it. Log out/in after changing the GNOME
  extension before testing new D-Bus methods. Older extensions may lack
  `RestorePreview`; the XWayland preview then falls back to EWMH unmaximize.

## Things that are easy to break

- **OS window management:** Changes in `core/screen/` can break X11, Wayland,
  or macOS behavior. Always test on the target OS. E2E arrangement tests run
  on X11 and on Wayland GNOME/KDE when the helper backend is enabled (GNOME
  needs the extension installed; KDE uses KWin scripting and needs nothing
  installed). GNOME disables extensions while the screen is locked, which makes
  the arrangement test fail with a clear message.
- **GNOME extension version gate:** GNOME Shell silently marks extensions
  `OUT OF DATE` when `metadata.json` `shell-version` does not list the running
  major version, so the E2E D-Bus check times out. Add new releases there.
- **E2E does not cover preview focus:** `E2EUtils.sendKeyToWindow` dispatches
  synthetic AWT events directly to the component, bypassing compositor/AWT focus,
  and `waitForPreviewWindow` only checks `isShowing`. When changing focus logic,
  add explicit window-active/focus-owner assertions (ideally with real key
  injection) rather than relying on arrangement tests.
- **Global hotkeys:** Linux evdev needs `/dev/input/event*` and `/dev/uinput`
  access. The Rust native lib must be rebuilt after changes. The grab+forward
  path can deliver one physical event twice (physical + virtual device); rapid
  callbacks are coalesced so one press produces one toggle.
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
