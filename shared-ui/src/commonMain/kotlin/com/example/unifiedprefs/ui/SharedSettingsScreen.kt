package com.example.unifiedprefs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.unifiedprefs.storage.PrefsRepository

/** Contract C6. */
@Composable
fun SharedSettingsScreen(repo: PrefsRepository, modifier: Modifier = Modifier) {
    val dark by remember(repo) { repo.observeBoolean(PrefKeys.darkMode) }
        .collectAsState(initial = repo.getBoolean(PrefKeys.darkMode))
    val observedName by remember(repo) { repo.observeString(PrefKeys.userName) }
        .collectAsState(initial = repo.getString(PrefKeys.userName))
    val count by remember(repo) { repo.observeInt(PrefKeys.launchCount) }
        .collectAsState(initial = repo.getInt(PrefKeys.launchCount))

    var name by remember { mutableStateOf(repo.getString(PrefKeys.userName)) }
    LaunchedEffect(observedName) {
        // Skip a value memory has already moved past, so fast typing never shows an older name.
        if (observedName == repo.getString(PrefKeys.userName) && observedName != name) {
            name = observedName
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Shared (CMP)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Dark mode")
                    Switch(
                        checked = dark,
                        onCheckedChange = { repo.putBoolean(PrefKeys.darkMode, it) },
                        modifier = Modifier.semantics { contentDescription = "Shared dark mode" },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        repo.putString(PrefKeys.userName, it)
                    },
                    label = { Text("Shared user name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Shared launch count: $count")
            }
        }
    }
}
