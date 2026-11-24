package com.meloberry.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meloberry.android.core.database.datastore.PreferencesDataStoreSource
import com.meloberry.android.core.model.ThemeMode
import com.meloberry.android.core.ui.theme.MeloberryTheme
import com.meloberry.android.ui.MeloberryApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStoreSource: PreferencesDataStoreSource

    private var incomingPackageUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            extractIncomingPackageUri(intent)?.let { incomingPackageUri = it }
        }
        setContent {
            val appThemeMode by preferencesDataStoreSource.appThemeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.BERRY)
            val scope = rememberCoroutineScope()

            MeloberryTheme(themeMode = appThemeMode) {
                MeloberryApp(
                    appThemeMode = appThemeMode,
                    onSelectAppThemeMode = { nextTheme ->
                        scope.launch { preferencesDataStoreSource.setAppThemeMode(nextTheme) }
                    },
                    incomingPackageUri = incomingPackageUri,
                    onIncomingPackageUriConsumed = { incomingPackageUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingPackageUri(intent)?.let { incomingPackageUri = it }
    }

    private fun extractIncomingPackageUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.data?.toString()?.takeIf { it.isNotBlank() }
    }
}
