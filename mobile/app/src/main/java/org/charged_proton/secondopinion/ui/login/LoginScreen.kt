package org.charged_proton.secondopinion.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.charged_proton.secondopinion.R
import org.charged_proton.secondopinion.presentation.login.LoginError
import org.charged_proton.secondopinion.presentation.login.LoginViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Sign-in gate: email/password form (sign in or create account) plus a
 * Google button that launches the account picker (ANDROID_APP.md §4).
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.login_email_label)) },
            enabled = !uiState.isBusy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.login_password_label)) },
            enabled = !uiState.isBusy,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!uiState.isSignUp) {
            TextButton(
                onClick = viewModel::onForgotPassword,
                enabled = uiState.canResetPassword,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(
                        if (uiState.isResettingPassword) {
                            R.string.login_sending_reset
                        } else {
                            R.string.login_forgot_password
                        },
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = viewModel::onSubmitEmail,
            enabled = uiState.canSubmitEmail,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (uiState.isSignUp) R.string.login_email_sign_up else R.string.login_email_sign_in,
                ),
            )
        }
        TextButton(onClick = viewModel::onToggleSignUp, enabled = !uiState.isBusy) {
            Text(
                stringResource(
                    if (uiState.isSignUp) R.string.login_switch_to_sign_in else R.string.login_switch_to_sign_up,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_or),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::onSignIn,
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_google))
        }

        if (uiState.passwordResetSent) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.login_reset_sent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(error.toStringRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun LoginError.toStringRes(): Int = when (this) {
    LoginError.SIGN_IN_FAILED -> R.string.login_error_sign_in_failed
    LoginError.INVALID_CREDENTIALS -> R.string.login_error_invalid_credentials
    LoginError.EMAIL_ALREADY_IN_USE -> R.string.login_error_email_in_use
    LoginError.WEAK_PASSWORD -> R.string.login_error_weak_password
    LoginError.INVALID_EMAIL -> R.string.login_error_invalid_email
    LoginError.PASSWORD_RESET_FAILED -> R.string.login_error_reset_failed
}
