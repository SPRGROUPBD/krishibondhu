package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdMonetizationManager
import com.example.ui.navigation.KrishiBondhuApp
import com.example.ui.theme.KrishiBondhuTheme
import com.example.ui.viewmodel.KrishiBondhuViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMonetizationManager.initialize(this)
        setContent {
            val viewModel: KrishiBondhuViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val notifPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* User preference respected; in-app inbox always remains active */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(uiState.adConfig.adsEnabled, uiState.adConfig.androidAdsEnabled, uiState.adConfig.androidInterstitialId) {
                AdMonetizationManager.preloadInterstitial(
                    context = this@MainActivity,
                    overrideUnitId = uiState.adConfig.androidInterstitialId,
                    enabled = uiState.adConfig.adsEnabled && uiState.adConfig.androidAdsEnabled
                )
            }

            KrishiBondhuTheme(darkTheme = uiState.darkMode) {
                KrishiBondhuApp(viewModel = viewModel)
            }
        }
    }
}
