package org.charged_proton.secondopinion.ui.legal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.charged_proton.secondopinion.R

enum class LegalDocument { TERMS, PRIVACY }

@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    Row(modifier = modifier) {
        TextButton(onClick = { document = LegalDocument.TERMS }) {
            Text(stringResource(R.string.legal_terms_title))
        }
        TextButton(onClick = { document = LegalDocument.PRIVACY }) {
            Text(stringResource(R.string.legal_privacy_title))
        }
    }
    document?.let {
        LegalDocumentDialog(document = it, onDismiss = { document = null })
    }
}

@Composable
fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    val title = when (document) {
        LegalDocument.TERMS -> R.string.legal_terms_title
        LegalDocument.PRIVACY -> R.string.legal_privacy_title
    }
    val body = when (document) {
        LegalDocument.TERMS -> R.string.legal_terms_body
        LegalDocument.PRIVACY -> R.string.legal_privacy_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Text(
                text = stringResource(body),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}