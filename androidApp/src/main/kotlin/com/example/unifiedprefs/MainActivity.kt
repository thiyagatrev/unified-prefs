package com.example.unifiedprefs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LegacyPrefs(applicationContext)
        setContent {
            var dark by remember { mutableStateOf(prefs.darkMode) }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        NativeSettings(prefs, dark) { dark = it; prefs.darkMode = it }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeSettings(prefs: LegacyPrefs, dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    var name by remember { mutableStateOf(prefs.userName) }
    val count = remember { prefs.launchCount }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Native (LegacyPrefs)", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dark mode", Modifier.weight(1f))
            Switch(
                checked = dark,
                onCheckedChange = onDarkChange,
                modifier = Modifier.semantics { contentDescription = "Native dark mode" },
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; prefs.userName = it },
            label = { Text("Native user name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Native launch count: $count")
    }
}
