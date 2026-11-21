package com.adrinand.gokfre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrinand.gokfre.viewmodel.SettingsViewModel

@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).testTag("general-settings-screen"),
    ) {
        Text(
            text = "General Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Launch at startup",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = viewModel.launchAtStartup,
                onCheckedChange = viewModel::updateLaunchAtStartup,
                modifier = Modifier.testTag("launch-at-startup-switch"),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hotkey Settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Toggle layout preview",
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        HotkeyCaptureInput(
            hotkey = viewModel.toggleHotkey,
            onHotkeyCaptured = { hotkey ->
                viewModel.toggleHotkey = hotkey
                viewModel.registrationError = null
            },
            onCapturingChanged = { capturing ->
                viewModel.isHotkeyCaptureActive = capturing
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )

        viewModel.registrationError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.testTag("hotkey-registration-error"),
            )
        }
    }

    viewModel.startupError?.let { message ->
        StartupErrorDialog(
            message = message,
            onDismiss = { viewModel.startupError = null },
        )
    }
}

@Composable
fun StartupErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Could not update startup setting") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        modifier = Modifier.testTag("startup-error-dialog"),
    )
}
