package com.adrinand.kofre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shows a warning explaining that the app needs access to Linux input devices.
 */
@Composable
fun InputPermissionWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input device access required") },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Kofre uses a low-level input listener for global hotkeys on Wayland. " +
                        "Your user account needs access to Linux input devices and /dev/uinput.",
                )
                Text(
                    "Add yourself to the 'input' and 'uinput' groups, then log out and back in:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "sudo usermod -aG input,uinput \$USER",
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Alternatively, bind Super+K in your compositor to run: kofre --toggle",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}
