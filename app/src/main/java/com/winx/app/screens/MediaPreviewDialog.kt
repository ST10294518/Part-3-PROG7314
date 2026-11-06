package com.winx.app.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.winx.app.data.remote.MediaItemDto
import com.winx.app.data.remote.MediaRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

@Composable
internal fun MediaPreviewDialog(
    media: MediaItemDto,
    repository: MediaRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var uri by remember(media.id) { mutableStateOf<Uri?>(null) }
    var error by remember(media.id) { mutableStateOf<String?>(null) }
    var attempt by remember(media.id) { mutableStateOf(0) }

    LaunchedEffect(media.id, media.filePath, repository, attempt) {
        uri = null
        error = null

        if (media.filePath.isNotBlank()) {
            val localUri = Uri.parse(media.filePath)
            if (localUri.scheme == "content") {
                uri = localUri
            } else {
                error = "This older attachment is unavailable on this device."
            }
            return@LaunchedEffect
        }

        val file = File(context.cacheDir, "winx-preview-${UUID.randomUUID()}")
        try {
            repository.downloadMedia(media.id, file).getOrElse {
                error = "Could not download this attachment. Check your connection and try again."
                return@LaunchedEffect
            }
            uri = Uri.fromFile(file)
            awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(media.fileName) },
        text = {
            val previewUri = uri
            val message = error
            when {
                message != null -> Column {
                    Text(message)
                    if (media.filePath.isBlank()) {
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
                    }
                }
                previewUri == null -> Text("Loading attachment...")
                media.mediaType.equals("Video", ignoreCase = true) -> AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                            setOnErrorListener { _, _, _ ->
                                error = "This video could not be played."
                                true
                            }
                            setVideoURI(previewUri)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    onRelease = { it.stopPlayback() }
                )
                else -> AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewUri)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = media.fileName,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    onError = { error = "This photo could not be opened." }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
