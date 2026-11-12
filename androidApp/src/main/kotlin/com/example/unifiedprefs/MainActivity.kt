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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedprefs.storage.PrefKeys
import com.example.unifiedprefs.storage.prefsRepository
import com.example.unifiedprefs.ui.SharedSettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LegacyPrefs(applicationContext)
        setContent {
            val repo = prefsRepository(this)
            val dark by remember(repo) { repo.observeBoolean(PrefKeys.darkMode) }
                .collectAsState(initial = repo.getBoolean(PrefKeys.darkMode))
            val storedName by remember(repo) { repo.observeString(PrefKeys.userName) }
                .collectAsState(initial = repo.getString(PrefKeys.userName))
            val count by remember(repo) { repo.observeInt(PrefKeys.launchCount) }
                .collectAsState(initial = repo.getInt(PrefKeys.launchCount))
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        NativeSettings(
                            dark = dark,
                            storedName = storedName,
                            count = count,
                            onDarkChange = { prefs.darkMode = it },
                            onNameChange = { prefs.userName = it },
                            latestName = { prefs.userName },
                        )
                        SharedSettingsScreen(repo)
                    }
                }
            }
        }
    }
}

@Composable
fun NativeSettings(
    dark: Boolean,
    storedName: String,
    count: Int,
    onDarkChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    latestName: () -> String,
) {
    var name by remember { mutableStateOf(storedName) }
    // Follow the observed value only when it is still the latest, so typing is never overwritten
    // by an older value that arrives late.
    LaunchedEffect(storedName) {
        if (storedName == latestName() && name != storedName) name = storedName
    }
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
            onValueChange = { name = it; onNameChange(it) },
            label = { Text("Native user name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Native launch count: $count")
    }
}
