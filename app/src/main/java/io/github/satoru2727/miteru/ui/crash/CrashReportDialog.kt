package io.github.satoru2727.miteru.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.satoru2727.miteru.R

private const val IssueUrl = "https://github.com/satoru2727/miteru/issues/new?template=bug_report.yml"

@Composable
fun CrashReportDialog(
    report: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.crash_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = report.take(6000),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.crash_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { shareText(context, report); onDismiss() }) {
                Text(stringResource(R.string.crash_share))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { copyAndOpenIssue(context, report); onDismiss() }) {
                    Text(stringResource(R.string.crash_github))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.crash_close))
                }
            }
        },
    )
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Miteru bug report")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.crash_share)))
}

fun copyAndOpenIssue(context: Context, report: String) {
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Miteru report", report))
    }
    Toast.makeText(context, context.getString(R.string.crash_copied), Toast.LENGTH_LONG).show()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IssueUrl)))
    }
}
