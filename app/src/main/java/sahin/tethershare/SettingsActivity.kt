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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sahin.tethershare.ui.theme.TetherShareTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePreferences(this)
        val authPrefs = AuthPreferences(this)

        setContent {
            var selectedTheme by remember { mutableIntStateOf(themePrefs.selectedTheme) }
            var isAuthEnabled by remember { mutableStateOf(authPrefs.isAuthEnabled) }
            var users by remember { mutableStateOf(authPrefs.getUsers()) }
            var showAddUserDialog by remember { mutableStateOf(false) }

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
                        isAuthEnabled = isAuthEnabled,
                        onAuthEnabledChanged = {
                            isAuthEnabled = it
                            authPrefs.isAuthEnabled = it
                        },
                        users = users,
                        onAddUserClick = { showAddUserDialog = true },
                        onRemoveUser = { user ->
                            authPrefs.removeUser(user)
                            users = authPrefs.getUsers()
                        },
                        onBack = { finish() }
                    )

                    if (showAddUserDialog) {
                        AddUserDialog(
                            onDismiss = { showAddUserDialog = false },
                            onConfirm = { user, pass ->
                                authPrefs.addUser(user, pass)
                                users = authPrefs.getUsers()
                                showAddUserDialog = false
                            }
                        )
                    }
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
    isAuthEnabled: Boolean,
    onAuthEnabledChanged: (Boolean) -> Unit,
    users: List<Pair<String, String>>,
    onAddUserClick: () -> Unit,
    onRemoveUser: (String) -> Unit,
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
                text = stringResource(R.string.proxy_auth),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.enable_auth), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isAuthEnabled, onCheckedChange = onAuthEnabledChanged)
            }

            if (isAuthEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.users), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onAddUserClick) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_user))
                    }
                }

                if (users.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_users),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    users.forEach { (user, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = user, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onRemoveUser(user) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

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
fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_user)) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (username.isNotBlank() && password.isNotBlank()) onConfirm(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
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
