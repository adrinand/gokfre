---
description: Re-run code and security review on the current diff.
---

Run the gokfre review agents on the current diff.

Steps:
1. Spawn the `reviewer` agent and collect its findings.
2. Spawn the `security-reviewer` agent and collect its findings.
3. Present a consolidated report grouped by severity.

Do not edit files yourself unless a finding is trivial and the user explicitly
asks you to fix it.
