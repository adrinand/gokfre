use kbd_global::binding_guard::BindingGuard;
use kbd_global::backend::Backend;
use kbd_global::manager::HotkeyManager;
use std::ffi::{c_char, CStr};
use std::os::raw::c_void;
use std::sync::Mutex;

pub struct ManagerHandle {
    manager: HotkeyManager,
    bindings: Mutex<Vec<BindingGuard>>,
}

fn create_manager() -> Result<(HotkeyManager, bool), kbd_global::error::StartupError> {
    // Try exclusive grab mode first so the Super key is not passed through to the
    // compositor (avoiding focus/Alt+Tab side effects on Wayland/X11). If the
    // uinput forwarding device is not available, fall back to passive listening.
    let grabbed = HotkeyManager::builder()
        .backend(Backend::Evdev)
        .grab()
        .build();
    match grabbed {
        Ok(manager) => Ok((manager, true)),
        Err(e) => {
            eprintln!("kofre_hotkey_init: grab mode unavailable ({}), falling back to non-grab", e);
            HotkeyManager::new().map(|manager| (manager, false))
        }
    }
}

/// Initializes the global hotkey manager.
///
/// Returns an opaque pointer to the manager on success, or null on error.
#[no_mangle]
pub extern "C" fn kofre_hotkey_init() -> *mut c_void {
    match create_manager() {
        Ok((manager, grabbed)) => {
            let handle = Box::new(ManagerHandle {
                manager,
                bindings: Mutex::new(Vec::new()),
            });
            eprintln!("kofre_hotkey_init: manager created (grab={})", grabbed);
            Box::into_raw(handle) as *mut c_void
        }
        Err(e) => {
            eprintln!("kofre_hotkey_init failed: {}", e);
            std::ptr::null_mut()
        }
    }
}

/// Registers a global hotkey.
///
/// # Safety
///
/// `manager` must be a valid pointer returned by [kofre_hotkey_init].
/// `combo` must be a valid, null-terminated UTF-8 string.
///
/// `combo` is a string like "Super+K" or "Ctrl+Shift+A".
/// `callback` is a C ABI function pointer invoked when the hotkey fires.
///
/// Returns 0 on success, non-zero on error.
#[no_mangle]
pub unsafe extern "C" fn kofre_hotkey_register(
    manager: *mut c_void,
    combo: *const c_char,
    callback: extern "C" fn(),
) -> i32 {
    if manager.is_null() {
        return 4;
    }
    if combo.is_null() {
        return 2;
    }

    let handle = &*(manager as *mut ManagerHandle);
    let combo_str = match CStr::from_ptr(combo).to_str() {
        Ok(s) => s,
        Err(_) => return 2,
    };

    match handle.manager.register(combo_str, move || {
        eprintln!("kofre_hotkey: combo '{}' fired", combo_str);
        callback();
    }) {
        Ok(guard) => {
            handle.bindings.lock().unwrap().push(guard);
            eprintln!("kofre_hotkey_register: registered '{}'", combo_str);
            0
        }
        Err(e) => {
            eprintln!("kofre_hotkey_register failed for '{}': {}", combo_str, e);
            3
        }
    }
}

/// Unregisters all hotkeys and shuts down the manager.
#[no_mangle]
pub extern "C" fn kofre_hotkey_shutdown(manager: *mut c_void) {
    if manager.is_null() {
        return;
    }
    unsafe {
        drop(Box::from_raw(manager as *mut ManagerHandle));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn init_and_register_shutdown() {
        let manager = kofre_hotkey_init();
        assert!(!manager.is_null());

        extern "C" fn callback() {}
        assert_eq!(
            unsafe { kofre_hotkey_register(manager, "Super+K\0".as_ptr() as *const c_char, callback) },
            0
        );

        kofre_hotkey_shutdown(manager);
    }

    #[test]
    fn register_with_null_manager_fails() {
        extern "C" fn callback() {}
        let result = unsafe { kofre_hotkey_register(std::ptr::null_mut(), "Super+K\0".as_ptr() as *const c_char, callback) };
        assert_eq!(result, 4);
    }
}
