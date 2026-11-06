package io.github.satoru2727.miteru.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.ui.CommentPostState

/** 投稿できるコメント色（ニコニコの色名 → 表示色）。 */
private val CommentColors = listOf(
    "white" to Color(0xFFFFEAEA),
    "red" to Color(0xFFF02840),
    "pink" to Color(0xFFFD7E80),
    "orange" to Color(0xFFFDA708),
    "yellow" to Color(0xFFFFE133),
    "green" to Color(0xFF64DD17),
    "cyan" to Color(0xFF00D4F5),
    "blue" to Color(0xFF4763FF),
)

private const val MaxCommentLength = 75

/**
 * ライブコメントの投稿シート。
 * 送信成功（Posted）で自動的に閉じる。失敗時は認識可能なメッセージを表示して開いたままにする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentInputSheet(
    postState: CommentPostState,
    onSend: (text: String, color: String, position: String, size: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("white") }
    var position by remember { mutableStateOf("naka") }
    var size by remember { mutableStateOf("medium") }
    val focusRequester = remember { FocusRequester() }

    // 送信に成功したらシートを閉じる
    LaunchedEffect(postState) {
        if (postState is CommentPostState.Posted) {
            onDismiss()
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.comment_send_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.comment_send_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.comment_color_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommentColors.forEach { (name, hex) ->
                    val selected = name == color
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(hex, CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { color = name },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.comment_position_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "naka" to stringResource(R.string.comment_position_flow),
                    "ue" to stringResource(R.string.comment_position_top),
                    "shita" to stringResource(R.string.comment_position_bottom),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = position == value,
                        onClick = { position = value },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.comment_size_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "small" to stringResource(R.string.settings_comment_size_small),
                    "medium" to stringResource(R.string.settings_comment_size_normal),
                    "big" to stringResource(R.string.settings_comment_size_large),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = size == value,
                        onClick = { size = value },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MaxCommentLength) },
                placeholder = { Text(stringResource(R.string.comment_send_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank() && postState !is CommentPostState.Posting) {
                            onSend(text, color, position, size)
                        }
                    },
                ),
            )
            if (postState is CommentPostState.Failed) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = postState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onSend(text, color, position, size) },
                enabled = text.isNotBlank() && postState !is CommentPostState.Posting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (postState is CommentPostState.Posting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.comment_send_button))
            }
        }
    }
}
