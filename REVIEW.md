# REVIEW.md — gokfre

Review instructions for this repo. Load this alongside `AGENTS.md` for every
code review.

## What failure looks like

A bad change in gokfre typically manifests as one of these user-visible
failures:

- The app crashes on startup or fails to show the system tray.
- The global hotkey (Super+K) stops registering on Linux or macOS.
- The preview window does not appear, does not have focus, or does not close
  after selecting keys.
- A selected tile does not move or resize the target window.
- Behavior diverges between X11, Wayland (GNOME/KDE), and macOS.
- Settings are lost, corrupted, or not loaded on the next launch.
- A native library fails to load or leaks resources.

## What to focus on

1. **Correctness on all supported platforms.** Any change to `core/screen/`,
   `core/hotkey/`, or the Rust library must consider X11, Wayland, and macOS.
2. **Resource lifecycle.** Native providers, sockets, file handles, and tray
   icons must be disposed/closed in the right order.
3. **Threading and coroutines.** Window-manager calls happen on `Dispatchers.IO`;
   Compose state must be read/updated on the main thread.
4. **Settings persistence.** `SettingsRepository` auto-saves; verify that
   mutations are observable and that the file is written atomically.
5. **Logging.** Use `java.util.logging` via the project logger. No `println` in
   production code.

## Cap the nits

- Do not flag style issues covered by detekt or ktlint.
- Do not ask for renames or minor formatting unless they materially improve
  readability or consistency with `AGENTS.md`.

## Paths to skip

- `build/`
- `.gradle/`
- `extensions/*/metadata.json`
- `src/main/resources/*` (icons, images)
- Generated Compose resource accessors

## Evidence style

Every finding must include a file path and line number. Do not guess from a
function name. If you cannot point to a specific line, do not report it.

## Severity tags

Tag each finding as one of:

- `Important` — likely bug, regression, or platform breakage.
- `Nit` — cleanup, naming, or minor improvement.
- `Pre-existing` — bug that already existed before this PR.

## Summary format

Start every review summary with a count, e.g.:

```
3 Important, 1 Nit, 0 Pre-existing
```

If there is nothing to fix, say plainly:

```
0 findings. The change looks good.
```

## Convergence rules

- After three review rounds, step back and reconsider the approach.
- Do not re-report findings that were already posted in an earlier round.
- When the same class of bug appears on multiple lines, flag the class and ask
  the author to fix every instance.
