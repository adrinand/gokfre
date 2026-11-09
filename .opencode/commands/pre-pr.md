---
description: Run the full pre-PR verification and review pipeline.
agent: verify
---

Run the gokfre pre-PR pipeline for the current working tree.

Steps:
1. Spawn the `verify` agent to run `./gradlew check`, `cargo test` (Linux), and
   `./gradlew e2e`. Wait for its report.
2. If verification passed, spawn the `reviewer` agent on the current diff and
   collect its findings.
3. Then spawn the `security-reviewer` agent on the current diff and collect its
   findings.
4. Present a consolidated report:
   - Verification status per check
   - Reviewer findings grouped by severity
   - Security reviewer findings grouped by severity
   - Recommended next action (fix, re-run, or open PR)

Do not edit files yourself unless a finding is trivial and the user explicitly
asks you to fix it.
