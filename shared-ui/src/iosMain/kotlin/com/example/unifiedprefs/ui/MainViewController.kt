package com.example.unifiedprefs.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.example.unifiedprefs.storage.IosStorage
import platform.UIKit.UIViewController

/** Contract C6. */
fun MainViewController(): UIViewController =
    ComposeUIViewController { SharedSettingsScreen(IosStorage.repo) }
