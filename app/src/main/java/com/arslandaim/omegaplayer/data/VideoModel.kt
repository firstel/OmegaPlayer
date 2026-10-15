/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class VideoModel(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long,
    val path: String,
    val documentFolder: String? = null
) {
    val isDocument: Boolean get() = documentFolder != null
    val folderName: String get() = documentFolder ?: (java.io.File(path).parentFile?.name ?: "Internal")
}
