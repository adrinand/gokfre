---
description: Run verification and review, then commit, push, and ensure a draft PR.
agent: build
---

Run the gokfre pre-PR pipeline for the current working tree.

Steps:
1. Spawn the `verify` agent to run `./gradlew check`, `cargo test` (Linux), and
   `./gradlew e2e`. Wait for its report. If verification failed, stop and report
   the failure; do not commit or push.
2. Spawn the `reviewer` agent on the current diff and collect its findings.
3. Spawn the `security-reviewer` agent on the current diff and collect its
   findings.
4. Address `Important` findings (or explain explicitly why they are out of
   scope), then re-run the affected verification.
5. Stage only the intended files and commit with a message matching the repo
   style.
6. Push the current branch with `git push -u origin HEAD`. Never finish with
   unpushed commits.
7. Ensure a draft PR exists: run `gh pr create --draft` when none exists,
   otherwise update the existing PR. Never mark a PR ready for review unless the
   user explicitly asks.
8. Report verification status per check, findings grouped by severity, and the
   PR URL (or the exact blocker that prevented pushing or opening it).

Review subagents are read-only, so you must perform the commit, push, and PR
steps yourself.
