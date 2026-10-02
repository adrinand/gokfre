package com.adrinand.kofre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrinand.kofre.viewmodel.SettingsViewModel

@Composable
fun HotkeySettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).testTag("hotkey-settings-screen"),
    ) {
        Text(
            text = "Hotkey Settings",
            fontSize = 20.sp,
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
}
