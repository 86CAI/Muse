/*
 * Muse — Android music player
 * Copyright (C) 2026 Cai & Caiyu
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details. You should have received a copy of the license along with this
 * program; if not, see <https://www.gnu.org/licenses/>.
 *
 * Muse contains code adapted from AGPL-3.0 and GPL-3.0 projects; see
 * THIRD_PARTY_NOTICES.md and COPYRIGHT.md for the full attribution list.
 */
package com.caipan.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.caipan.music.ui.MainScreen
import com.caipan.music.ui.components.SplashScreen
import com.caipan.music.ui.onboarding.OnboardingPrefs
import com.caipan.music.ui.onboarding.OnboardingScreen
import com.caipan.music.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingDone = getSharedPreferences("muse_prefs", 0).getBoolean("onboarding_complete", false)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var showOnboarding by remember { mutableStateOf(!onboardingDone) }
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MuseApplication
            MusicPlayerTheme(skin = app.skinManager.activeSkin()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    when {
                        showSplash -> SplashScreen(onFinished = { showSplash = false })
                        showOnboarding -> OnboardingScreen(onFinished = { showOnboarding = false })
                        else -> MainScreen()
                    }
                }
            }
        }
    }
}
