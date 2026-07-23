package com.twocircle.bike.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twocircle.bike.designsystem.R

/**
 * Register screen — name, email, password, terms checkbox.
 */
@Composable
fun RegisterScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is RegisterUiState.Success -> {
                onSuccess()
            }
            is RegisterUiState.Error -> {
                val errorMsg = when (state.error) {
                    AuthError.EmptyName -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_empty_name)
                    AuthError.InvalidEmail -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_invalid_email)
                    AuthError.WeakPassword -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_weak_password)
                    AuthError.PasswordMismatch -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_password_mismatch)
                    AuthError.TermsNotAccepted -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_terms)
                    AuthError.EmailTaken -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_email_taken)
                    AuthError.NetworkError -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_network)
                    else -> context.getString(com.twocircle.bike.designsystem.R.string.auth_error_generic)
                }
                snackbarHostState.showSnackbar(errorMsg)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.auth_register_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.auth_display_name)) },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Confirm Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.auth_confirm_password)) },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Terms Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
            )
            Text(
                text = stringResource(R.string.auth_terms_checkbox),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Button
        Button(
            onClick = { viewModel.register(name, email, password, confirmPassword, termsAccepted) },
            enabled = uiState !is RegisterUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is RegisterUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.auth_register_button))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back to Login
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.auth_back_to_login))
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}
