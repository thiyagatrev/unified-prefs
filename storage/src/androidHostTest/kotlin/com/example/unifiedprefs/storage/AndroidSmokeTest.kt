package com.example.unifiedprefs.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSmokeTest {
    @Test
    fun applicationContextIsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.packageName.isNotEmpty())
    }
}
