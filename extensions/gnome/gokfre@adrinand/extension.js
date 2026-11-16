import Gio from 'gi://Gio';
import Meta from 'gi://Meta';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const DBUS_INTERFACE = `
<node>
  <interface name="org.gnome.Shell.Extensions.Gokfre">
    <method name="CaptureActiveWindow">
      <arg type="b" direction="out" name="success"/>
      <arg type="i" direction="out" name="x"/>
      <arg type="i" direction="out" name="y"/>
      <arg type="i" direction="out" name="width"/>
      <arg type="i" direction="out" name="height"/>
    </method>
    <method name="GetCapturedWindow">
      <arg type="b" direction="out" name="hasWindow"/>
      <arg type="s" direction="out" name="title"/>
      <arg type="i" direction="out" name="x"/>
      <arg type="i" direction="out" name="y"/>
      <arg type="i" direction="out" name="width"/>
      <arg type="i" direction="out" name="height"/>
    </method>
    <method name="MoveResizeCapturedWindow">
      <arg type="i" direction="in" name="x"/>
      <arg type="i" direction="in" name="y"/>
      <arg type="i" direction="in" name="width"/>
      <arg type="i" direction="in" name="height"/>
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="FocusCapturedWindow">
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="MaximizePreview">
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="PlacePreview">
      <arg type="i" direction="in" name="x"/>
      <arg type="i" direction="in" name="y"/>
      <arg type="i" direction="in" name="width"/>
      <arg type="i" direction="in" name="height"/>
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="PlacePreviewForPid">
      <arg type="i" direction="in" name="x"/>
      <arg type="i" direction="in" name="y"/>
      <arg type="i" direction="in" name="width"/>
      <arg type="i" direction="in" name="height"/>
      <arg type="i" direction="in" name="pid"/>
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="ShowPreview">
      <arg type="i" direction="in" name="x"/>
      <arg type="i" direction="in" name="y"/>
      <arg type="i" direction="in" name="width"/>
      <arg type="i" direction="in" name="height"/>
      <arg type="i" direction="in" name="pid"/>
      <arg type="b" direction="out" name="placed"/>
      <arg type="b" direction="out" name="focused"/>
    </method>
    <method name="FocusPreview">
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="FocusPreviewForPid">
      <arg type="i" direction="in" name="pid"/>
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="RestorePreview">
      <arg type="b" direction="out" name="success"/>
    </method>
    <method name="IsPreviewMaximized">
      <arg type="b" direction="out" name="maximized"/>
    </method>
  </interface>
</node>
`;

const DBUS_NAME = 'org.gnome.Shell.Extensions.Gokfre';
const DBUS_PATH = '/org/gnome/Shell/Extensions/Gokfre';
const PREVIEW_TITLE = 'Gokfre Preview';

// Mutter 49 replaced get_maximized() and the maximize/unmaximize flags
// arguments with get_maximize_flags() and no-argument methods.
function getMaximizeFlags(window) {
    if (typeof window.get_maximize_flags === 'function') {
        return window.get_maximize_flags();
    }
    return window.get_maximized();
}

function setMaximized(window, maximized) {
    if (typeof window.get_maximize_flags === 'function') {
        if (maximized) {
            window.maximize();
        } else {
            window.unmaximize();
        }
        return;
    }
    if (maximized) {
        window.maximize(Meta.MaximizeFlags.BOTH);
    } else {
        window.unmaximize(Meta.MaximizeFlags.BOTH);
    }
}

let capturedWindow = null;

function findPreviewWindow() {
    return global.get_window_actors()
        .map(actor => actor.metaWindow)
        .find(window => window.title === PREVIEW_TITLE);
}

function isWindowMapped(window) {
    if (typeof window.is_mapped === 'function') {
        return window.is_mapped();
    }
    if (typeof window.mapped === 'boolean') {
        return window.mapped;
    }
    return true;
}

function monitorIndexForRect(x, y, width, height) {
    const centerX = x + width / 2;
    const centerY = y + height / 2;
    for (let monitor = 0; monitor < global.display.get_n_monitors(); monitor++) {
        const geometry = global.display.get_monitor_geometry(monitor);
        if (
            centerX >= geometry.x &&
            centerX < geometry.x + geometry.width &&
            centerY >= geometry.y &&
            centerY < geometry.y + geometry.height
        ) {
            return monitor;
        }
    }
    return -1;
}

function isPreviewOwnedBy(window, ownerPid) {
    if (typeof window.get_pid !== 'function') {
        return true;
    }
    const windowPid = window.get_pid();
    return windowPid <= 0 || windowPid === ownerPid;
}

function isPreviewBoundsOnScreen(x, y, width, height) {
    if (width <= 0 || height <= 0) {
        return false;
    }
    for (let monitor = 0; monitor < global.display.get_n_monitors(); monitor++) {
        const geometry = global.display.get_monitor_geometry(monitor);
        const left = Math.max(x, geometry.x);
        const top = Math.max(y, geometry.y);
        const right = Math.min(x + width, geometry.x + geometry.width);
        const bottom = Math.min(y + height, geometry.y + geometry.height);
        if (right > left && bottom > top) {
            return true;
        }
    }
    return false;
}

class GokfreDBus {
    CaptureActiveWindow() {
        capturedWindow = global.display.focus_window;
        if (!capturedWindow) {
            return [false, 0, 0, 0, 0];
        }
        try {
            const monitor = capturedWindow.get_monitor();
            const workspace = global.workspace_manager.get_active_workspace();
            const rect = workspace.get_work_area_for_monitor(monitor);
            return [true, rect.x, rect.y, rect.width, rect.height];
        } catch (e) {
            console.error('[gokfre] CaptureActiveWindow work area error: ' + e);
            return [true, 0, 0, 0, 0];
        }
    }

    GetCapturedWindow() {
        if (!capturedWindow) {
            return [false, '', 0, 0, 0, 0];
        }
        const rect = capturedWindow.get_frame_rect();
        return [true, capturedWindow.title || '', rect.x, rect.y, rect.width, rect.height];
    }

    MoveResizeCapturedWindow(x, y, width, height) {
        if (!capturedWindow) {
            return [false];
        }
        try {
            if (getMaximizeFlags(capturedWindow) !== 0) {
                setMaximized(capturedWindow, false);
            }
            capturedWindow.move_resize_frame(true, x, y, width, height);
            return [true];
        } catch (e) {
            console.error('[gokfre] MoveResizeCapturedWindow error: ' + e);
            return [false];
        }
    }

    FocusCapturedWindow() {
        if (!capturedWindow) {
            return [false];
        }
        try {
            const time = global.display.get_current_time_roundtrip();
            capturedWindow.activate(time);
            return [true];
        } catch (e) {
            console.error('[gokfre] FocusCapturedWindow error: ' + e);
            return [false];
        }
    }

    MaximizePreview() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        try {
            if (getMaximizeFlags(preview) !== Meta.MaximizeFlags.BOTH) {
                setMaximized(preview, true);
            }
            return [true];
        } catch (e) {
            console.error('[gokfre] MaximizePreview error: ' + e);
            return [false];
        }
    }

    PlacePreview(x, y, width, height) {
        const preview = findPreviewWindow();
        if (!preview || !isWindowMapped(preview) || !isPreviewBoundsOnScreen(x, y, width, height)) {
            return [false];
        }
        try {
            if (getMaximizeFlags(preview) !== 0) {
                setMaximized(preview, false);
            }
            preview.move_resize_frame(true, x, y, width, height);
            setMaximized(preview, true);
            return [true];
        } catch (e) {
            console.error('[gokfre] PlacePreview error: ' + e);
            return [false];
        }
    }

    PlacePreviewForPid(x, y, width, height, pid) {
        const preview = findPreviewWindow();
        if (
            !preview ||
            !isWindowMapped(preview) ||
            !isPreviewOwnedBy(preview, pid) ||
            !isPreviewBoundsOnScreen(x, y, width, height)
        ) {
            return [false];
        }
        try {
            if (getMaximizeFlags(preview) !== 0) {
                setMaximized(preview, false);
            }
            preview.move_resize_frame(true, x, y, width, height);
            setMaximized(preview, true);
            return [true];
        } catch (e) {
            console.error('[gokfre] PlacePreviewForPid error: ' + e);
            return [false];
        }
    }

    ShowPreview(x, y, width, height, pid) {
        const preview = findPreviewWindow();
        if (
            !preview ||
            !isWindowMapped(preview) ||
            !isPreviewOwnedBy(preview, pid) ||
            !isPreviewBoundsOnScreen(x, y, width, height)
        ) {
            return [false, false];
        }
        try {
            const targetMonitor = monitorIndexForRect(x, y, width, height);
            const maximized = getMaximizeFlags(preview) === Meta.MaximizeFlags.BOTH;
            if (preview.get_monitor() === targetMonitor && maximized) {
                const time = global.display.get_current_time_roundtrip();
                Main.activateWindow(preview, time);
                return [true, preview.has_focus()];
            }
            if (getMaximizeFlags(preview) !== 0) {
                setMaximized(preview, false);
            }
            preview.move_resize_frame(true, x, y, width, height);
            setMaximized(preview, true);
            const time = global.display.get_current_time_roundtrip();
            Main.activateWindow(preview, time);
            return [true, preview.has_focus()];
        } catch (e) {
            console.error('[gokfre] ShowPreview error: ' + e);
            return [false, false];
        }
    }

    FocusPreview() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        try {
            const time = global.display.get_current_time_roundtrip();
            Main.activateWindow(preview, time);
            return [preview.has_focus()];
        } catch (e) {
            console.error('[gokfre] FocusPreview error: ' + e);
            return [false];
        }
    }

    FocusPreviewForPid(pid) {
        const preview = findPreviewWindow();
        if (!preview || !isWindowMapped(preview) || !isPreviewOwnedBy(preview, pid)) {
            return [false];
        }
        try {
            const time = global.display.get_current_time_roundtrip();
            Main.activateWindow(preview, time);
            return [preview.has_focus()];
        } catch (e) {
            console.error('[gokfre] FocusPreviewForPid error: ' + e);
            return [false];
        }
    }

    RestorePreview() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        try {
            if (getMaximizeFlags(preview) !== 0) {
                setMaximized(preview, false);
            }
            return [true];
        } catch (e) {
            console.error('[gokfre] RestorePreview error: ' + e);
            return [false];
        }
    }

    IsPreviewMaximized() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        return [getMaximizeFlags(preview) === Meta.MaximizeFlags.BOTH];
    }
}

export default class GokfreExtension extends Extension {
    enable() {
        try {
            this._dbus = Gio.DBusExportedObject.wrapJSObject(DBUS_INTERFACE, new GokfreDBus());
            this._dbus.export(Gio.DBus.session, DBUS_PATH);
            this._busNameId = Gio.bus_own_name(
                Gio.BusType.SESSION,
                DBUS_NAME,
                Gio.BusNameOwnerFlags.NONE,
                () => console.log('[gokfre] acquired D-Bus name ' + DBUS_NAME),
                null,
                null,
            );
            console.log('[gokfre] D-Bus service exported at ' + DBUS_PATH);
        } catch (e) {
            console.error('[gokfre] Failed to export D-Bus service: ' + e);
        }
    }

    disable() {
        if (this._busNameId !== undefined) {
            Gio.bus_unown_name(this._busNameId);
            this._busNameId = undefined;
        }
        if (this._dbus) {
            this._dbus.unexport();
            this._dbus = null;
        }
        capturedWindow = null;
    }
}