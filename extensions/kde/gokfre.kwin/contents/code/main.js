function getActiveWindow() {
    const client = workspace.activeWindow;
    if (!client) {
        return [false, '', 0, 0, 0, 0];
    }
    const rect = client.frameGeometry;
    return [true, client.caption || '', rect.x, rect.y, rect.width, rect.height];
}

function moveResizeActiveWindow(x, y, width, height) {
    const client = workspace.activeWindow;
    if (!client) {
        return false;
    }
    client.frameGeometry = {x: x, y: y, width: width, height: height};
    return true;
}

function focusActiveWindow() {
    const client = workspace.activeWindow;
    if (!client) {
        return false;
    }
    workspace.activeWindow = client;
    return true;
}

const PREVIEW_TITLE = 'Gokfre Preview';

function findPreviewWindow() {
    const clients = workspace.windowList ? workspace.windowList() : [];
    return clients.find(client => client.caption === PREVIEW_TITLE);
}

function maximizePreview() {
    const preview = findPreviewWindow();
    if (!preview) {
        return false;
    }
    if (preview.setMaximize) {
        preview.setMaximize(true, true);
    } else if (preview.maximized !== undefined) {
        preview.maximized = 2; // MaximizeMode.Maximized
    }
    return true;
}

function isPreviewMaximized() {
    const preview = findPreviewWindow();
    if (!preview) {
        return false;
    }
    if (preview.maximized !== undefined) {
        return preview.maximized === 2; // MaximizeMode.Maximized
    }
    return false;
}

registerDBusService('org.kde.KWin.Script.Gokfre', '/Gokfre', {
    getActiveWindow: getActiveWindow,
    moveResizeActiveWindow: moveResizeActiveWindow,
    focusActiveWindow: focusActiveWindow,
    maximizePreview: maximizePreview,
    isPreviewMaximized: isPreviewMaximized,
});
