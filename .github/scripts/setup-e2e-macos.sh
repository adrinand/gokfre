#!/bin/bash
# Sets up a macOS environment for E2E tests.
# JNativeHook requires Accessibility permission for the test JVM. On GitHub-
# hosted runners this cannot be granted via the normal UI prompt, so the tests
# may skip global-hotkey assertions. Startup/persistence/single-instance tests
# do not require Accessibility.
set -euo pipefail

# Enable automation mode so accessibility-related prompts do not block CI.
# This is a best-effort step; it does not grant Accessibility to arbitrary
# processes, but it allows system dialogs to be scripted.
if command -v automationmodetool >/dev/null 2>&1; then
    sudo automationmodetool enable-automationmode-without-authentication || true
fi

# Attempt to pre-approve the Java/JUnit test runner for Accessibility.
# This only works when the TCC database is writable in the runner image.
TCC_DB="/Library/Application Support/com.apple.TCC/TCC.db"
JVM_PATH="$(/usr/libexec/java_home)/bin/java"

if [ -w "$TCC_DB" ]; then
    echo "Granting Accessibility to $JVM_PATH"
    sqlite3 "$TCC_DB" \
        "INSERT OR REPLACE INTO access (service, client, client_type, auth_value, auth_reason, auth_version)
         VALUES ('kTCCServiceAccessibility', '$JVM_PATH', 1, 2, 3, 1);" || true
fi

# Persist any variables that subsequent steps may need.
{
    echo "XDG_SESSION_TYPE=macos"
    echo "XDG_CURRENT_DESKTOP=macos"
} >> "$GITHUB_ENV"
