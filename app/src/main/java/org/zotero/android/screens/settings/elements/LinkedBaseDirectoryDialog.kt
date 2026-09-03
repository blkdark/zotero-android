package org.zotero.android.screens.settings.elements

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.zotero.android.files.LinkedFileResolver
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import timber.log.Timber

@Composable
internal fun LinkedBaseDirectoryDialog(
    currentPath: String?,
    onSave: (path: String?, uri: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pathText by remember { mutableStateOf(currentPath ?: "") }
    var selectedTreeUri by remember { mutableStateOf<String?>(null) }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Timber.e(e, "LinkedBaseDirectoryDialog: Could not take persistable permission")
            }

            val resolved = LinkedFileResolver.getPathFromTreeUri(uri)
            selectedTreeUri = uri.toString()
            pathText = resolved ?: uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = Strings.settings_linked_attachment_base_dir_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = Strings.settings_linked_attachment_base_dir_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { openDocumentTreeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = Drawables.folder_open_24px),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = stringResource(id = Strings.settings_linked_attachment_base_dir_choose_folder))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pathText,
                    onValueChange = {
                        pathText = it
                        selectedTreeUri = null
                    },
                    label = { Text("Ruta / Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(pathText, selectedTreeUri)
                }
            ) {
                Text(text = "Guardar")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentPath != null || pathText.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onSave(null, null)
                        }
                    ) {
                        Text(
                            text = stringResource(id = Strings.settings_linked_attachment_base_dir_clear),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancelar")
                }
            }
        }
    )
}
