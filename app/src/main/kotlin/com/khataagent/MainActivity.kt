package com.khataagent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.app.AppViewModel
import com.khataagent.onboarding.LocaleManager
import com.khataagent.onboarding.OnboardingScreen
import com.khataagent.ui.nav.KhataNav
import com.khataagent.ui.theme.KhataTheme

/**
 * Single activity. Installs [KhataTheme] + [KhataNav] wired to the real local-loop stack from
 * [AppViewModel] (Room repository + on-device Gemma orchestrator); escalation/connectivity stay
 * on the container's fakes for the Insights demo.
 *
 * First run shows [OnboardingScreen] (language picker) before anything else; on every later
 * launch the saved language is applied and the app goes straight to [KhataNav].
 */
class MainActivity : ComponentActivity() {

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* voice path only */ }

    override fun attachBaseContext(newBase: Context) {
        // Apply the shopkeeper's saved language (if any) before any resources/strings are read.
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        val localeManager = LocaleManager(this)

        setContent {
            var languageChosen by remember { mutableStateOf(localeManager.isLanguageChosen()) }

            if (!languageChosen) {
                KhataTheme {
                    OnboardingScreen(
                        onDone = {
                            languageChosen = true
                            // Recreate so attachBaseContext re-wraps the whole app with the newly
                            // saved locale — every screen (not just this composition) picks it up.
                            recreate()
                        },
                    )
                }
            } else {
                val appViewModel: AppViewModel = viewModel()
                val agentStatus by appViewModel.statusController.status.collectAsState()
                val isOnline by appViewModel.connectivity.isOnline.collectAsState()

                KhataTheme {
                    KhataNav(
                        repository = appViewModel.repository,
                        orchestrator = appViewModel.orchestrator,
                        audioRecorder = appViewModel.audioRecorder,
                        voiceAvailable = appViewModel.voiceAvailable,
                        cloudAvailable = appViewModel.cloudAvailable,
                        escalationClient = appViewModel.escalationClient,
                        connectivityMonitor = appViewModel.connectivity,
                        statusController = appViewModel.statusController,
                        agentStatus = agentStatus,
                        isOnline = isOnline,
                        onToggleConnectivity = { appViewModel.connectivity.toggle() },
                    )
                }
            }
        }
    }
}
