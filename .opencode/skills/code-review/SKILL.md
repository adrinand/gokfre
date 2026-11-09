---
name: code-review
description: Use when reviewing code or running /pre-pr. Provides shared review style rules for all gokfre reviewers.
---

# Code review skill

When reviewing code in this repo:

1. Read `AGENTS.md` and `REVIEW.md` first.
2. Focus on bugs, platform-specific breakage, resource leaks, threading, and
   persistence issues. Skip style nits covered by detekt/ktlint.
3. Provide file/line evidence for every finding.
4. Tag findings `Important`, `Nit`, or `Pre-existing`.
5. Start the summary with a count (`3 Important, 1 Nit, 0 Pre-existing`) or
   say plainly `0 findings` if clean.
6. After three review rounds, stop and ask the author to reconsider the
   approach.
7. Do not re-report already-posted findings.
