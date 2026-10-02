import Gio from 'gi://Gio';
import Meta from 'gi://Meta';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const DBUS_INTERFACE = `
<node>
  <interface name="org.gnome.Shell.Extensions.Kofre">
    <method name="CaptureActiveWindow">
      <arg type="b" direction="out" name="success"/>
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
    <method name="IsPreviewMaximized">
      <arg type="b" direction="out" name="maximized"/>
    </method>
  </interface>
</node>
`;

const DBUS_NAME = 'org.gnome.Shell.Extensions.Kofre';
const DBUS_PATH = '/org/gnome/Shell/Extensions/Kofre';
const PREVIEW_TITLE = 'Kofre Preview';

let capturedWindow = null;

function findPreviewWindow() {
    return global.get_window_actors()
        .map(actor => actor.metaWindow)
        .find(window => window.title === PREVIEW_TITLE);
}

class KofreDBus {
    CaptureActiveWindow() {
        capturedWindow = global.display.focus_window;
        if (!capturedWindow) {
            return [false];
        }
        return [true];
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
            if (capturedWindow.get_maximized() !== 0) {
                capturedWindow.unmaximize(Meta.MaximizeFlags.BOTH);
            }
            capturedWindow.move_resize_frame(true, x, y, width, height);
            return [true];
        } catch (e) {
            console.error('[kofre] MoveResizeCapturedWindow error: ' + e);
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
            console.error('[kofre] FocusCapturedWindow error: ' + e);
            return [false];
        }
    }

    MaximizePreview() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        try {
            if (preview.get_maximized() !== Meta.MaximizeFlags.BOTH) {
                preview.maximize(Meta.MaximizeFlags.BOTH);
            }
            return [true];
        } catch (e) {
            console.error('[kofre] MaximizePreview error: ' + e);
            return [false];
        }
    }

    IsPreviewMaximized() {
        const preview = findPreviewWindow();
        if (!preview) {
            return [false];
        }
        return [preview.get_maximized() === Meta.MaximizeFlags.BOTH];
    }
}

export default class KofreExtension extends Extension {
    enable() {
        try {
            this._dbus = Gio.DBusExportedObject.wrapJSObject(DBUS_INTERFACE, new KofreDBus());
            this._dbus.export(Gio.DBus.session, DBUS_PATH);
            this._busNameId = Gio.bus_own_name(
                Gio.BusType.SESSION,
                DBUS_NAME,
                Gio.BusNameOwnerFlags.NONE,
                () => console.log('[kofre] acquired D-Bus name ' + DBUS_NAME),
                null,
                null,
            );
            console.log('[kofre] D-Bus service exported at ' + DBUS_PATH);
        } catch (e) {
            console.error('[kofre] Failed to export D-Bus service: ' + e);
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