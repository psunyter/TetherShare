package sahin.tethershare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sahin.tethershare.ui.theme.TetherShareTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePreferences(this)

        setContent {
            var selectedTheme by remember { mutableIntStateOf(themePrefs.selectedTheme) }

            TetherShareTheme(themeMode = selectedTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        selectedTheme = selectedTheme,
                        onThemeSelected = {
                            selectedTheme = it
                            themePrefs.selectedTheme = it
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTheme: Int,
    onThemeSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            ThemeOption(
                label = stringResource(R.string.theme_system),
                selected = selectedTheme == ThemePreferences.THEME_SYSTEM,
                onClick = { onThemeSelected(ThemePreferences.THEME_SYSTEM) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_light),
                selected = selectedTheme == ThemePreferences.THEME_LIGHT,
                onClick = { onThemeSelected(ThemePreferences.THEME_LIGHT) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_dark),
                selected = selectedTheme == ThemePreferences.THEME_DARK,
                onClick = { onThemeSelected(ThemePreferences.THEME_DARK) }
            )
            ThemeOption(
                label = stringResource(R.string.theme_amoled),
                selected = selectedTheme == ThemePreferences.THEME_AMOLED,
                onClick = { onThemeSelected(ThemePreferences.THEME_AMOLED) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
