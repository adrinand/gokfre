// Runs inside KWin's script engine (org.kde.KWin.Scripting). KWin scripts
// cannot own D-Bus names, so results are reported back via callDBus().
(function () {
    'use strict';

    var REQUEST = {
        token: __GOKFRE_TOKEN__,
        operation: __GOKFRE_OPERATION__,
        windowId: __GOKFRE_WINDOW_ID__,
        x: __GOKFRE_X__,
        y: __GOKFRE_Y__,
        width: __GOKFRE_WIDTH__,
        height: __GOKFRE_HEIGHT__
    };

    var BRIDGE_SERVICE = 'org.kde.gokfre.Bridge';
    var BRIDGE_PATH = '/org/kde/gokfre/Bridge';
    var BRIDGE_INTERFACE = 'org.kde.gokfre.Bridge';
    var PREVIEW_TITLE = __GOKFRE_PREVIEW_TITLE__;
    var MAXIMIZE_FULL = 3;

    // Plasma 5 exposes activeClient/clientList; Plasma 6 renamed them to
    // activeWindow/windowList. Support both.
    function activeWindow() {
        if (typeof workspace.activeWindow !== 'undefined') {
            return workspace.activeWindow;
        }
        if (typeof workspace.activeClient !== 'undefined') {
            return workspace.activeClient;
        }
        return null;
    }

    function allWindows() {
        if (typeof workspace.windowList === 'function') {
            return workspace.windowList();
        }
        if (typeof workspace.clientList === 'function') {
            return workspace.clientList();
        }
        return [];
    }

    function findWindowById(id) {
        var windows = allWindows();
        for (var i = 0; i < windows.length; i++) {
            if (String(windows[i].internalId) === String(id)) {
                return windows[i];
            }
        }
        return null;
    }

    function previewWindow() {
        var windows = allWindows();
        for (var i = 0; i < windows.length; i++) {
            if ((windows[i].caption || '').indexOf(PREVIEW_TITLE) !== -1) {
                return windows[i];
            }
        }
        return null;
    }

    function activate(window) {
        if (!window) {
            return false;
        }
        try {
            if (typeof workspace.activeWindow !== 'undefined') {
                workspace.activeWindow = window;
                return true;
            }
            if (typeof workspace.activeClient !== 'undefined') {
                workspace.activeClient = window;
                return true;
            }
        } catch (e) {
            return false;
        }
        return false;
    }

    function unmaximize(window) {
        if (!window) {
            return;
        }
        try {
            window.setMaximize(false, false);
        } catch (e) {
            // Window is not maximizable or has already been restored.
        }
    }

    function isMaximized(window) {
        return typeof window.maximizeMode !== 'undefined' &&
            window.maximizeMode === MAXIMIZE_FULL;
    }

    function frameGeometry(window) {
        var rect = window.frameGeometry;
        return {
            x: Math.round(rect.x),
            y: Math.round(rect.y),
            width: Math.round(rect.width),
            height: Math.round(rect.height)
        };
    }

    function report(result) {
        try {
            callDBus(
                BRIDGE_SERVICE,
                BRIDGE_PATH,
                BRIDGE_INTERFACE,
                'Report',
                REQUEST.token,
                JSON.stringify(result)
            );
        } catch (e) {
            if (typeof console !== 'undefined') {
                console.error('[gokfre] failed to report result: ' + e);
            }
        }
    }

    function capture() {
        var window = activeWindow();
        if (!window) {
            report({ ok: false, error: 'no active window' });
            return;
        }
        var geometry = frameGeometry(window);
        report({
            ok: true,
            id: typeof window.internalId !== 'undefined' ? String(window.internalId) : '',
            title: window.caption || '',
            x: geometry.x,
            y: geometry.y,
            width: geometry.width,
            height: geometry.height
        });
    }

    function focus() {
        var window = findWindowById(REQUEST.windowId) || activeWindow();
        report({ ok: activate(window) });
    }

    function bounds() {
        var window = findWindowById(REQUEST.windowId) || activeWindow();
        if (!window) {
            report({ ok: false, error: 'no window' });
            return;
        }
        var geometry = frameGeometry(window);
        report({
            ok: true,
            x: geometry.x,
            y: geometry.y,
            width: geometry.width,
            height: geometry.height
        });
    }

    function moveResize() {
        var window = findWindowById(REQUEST.windowId) || activeWindow();
        if (!window) {
            report({ ok: false, error: 'no window' });
            return;
        }
        unmaximize(window);
        var target = {
            x: REQUEST.x,
            y: REQUEST.y,
            width: REQUEST.width,
            height: REQUEST.height
        };
        try {
            window.frameGeometry = target;
        } catch (e) {
            // Fall through to the geometry alias below.
        }
        var applied = frameGeometry(window);
        if (applied.x === target.x && applied.y === target.y &&
                applied.width === target.width && applied.height === target.height) {
            report({ ok: true });
            return;
        }
        // Plasma 5 also exposes the deprecated `geometry` alias.
        try {
            window.geometry = target;
        } catch (e) {
            // Give up; the report below tells gokfre the move failed.
        }
        applied = frameGeometry(window);
        report({
            ok: applied.x === target.x && applied.y === target.y &&
                applied.width === target.width && applied.height === target.height
        });
    }

    function maximizePreview() {
        var window = previewWindow();
        if (!window) {
            report({ ok: false, error: 'preview window not found' });
            return;
        }
        var applied = false;
        try {
            window.setMaximize(true, true);
            applied = true;
        } catch (e) {
            applied = false;
        }
        report({ ok: applied, maximized: isMaximized(window) });
    }

    function isPreviewMaximized() {
        var window = previewWindow();
        if (!window) {
            report({ ok: false, error: 'preview window not found' });
            return;
        }
        report({ ok: true, maximized: isMaximized(window) });
    }

    function restorePreview() {
        var window = previewWindow();
        if (!window) {
            report({ ok: false, error: 'preview window not found' });
            return;
        }
        unmaximize(window);
        report({ ok: true, maximized: isMaximized(window) });
    }

    function probe() {
        report({ ok: true });
    }

    try {
        switch (REQUEST.operation) {
        case 'capture':
            capture();
            break;
        case 'focus':
            focus();
            break;
        case 'bounds':
            bounds();
            break;
        case 'moveResize':
            moveResize();
            break;
        case 'maximizePreview':
            maximizePreview();
            break;
        case 'isPreviewMaximized':
            isPreviewMaximized();
            break;
        case 'restorePreview':
            restorePreview();
            break;
        case 'probe':
            probe();
            break;
        default:
            report({ ok: false, error: 'unknown operation: ' + REQUEST.operation });
        }
    } catch (e) {
        report({ ok: false, error: String(e) });
    }
})();
