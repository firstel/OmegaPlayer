package com.arslandaim.omegaplayer.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import com.arslandaim.omegaplayer.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class MainLandscapeTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        val file = File(compose.activity.getExternalFilesDir(null), "layout-captures/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun populatedLibraryAndNavigationFitLandscape() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Landscape test.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OmegaLayoutCheck")
        })!!
        try {
            compose.waitUntil(30_000) { compose.onAllNodesWithText("OmegaLayoutCheck").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Folders", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("No", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("1 folder", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("OmegaLayoutCheck").assertIsDisplayed()
            screenshot("library-landscape")
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.waitForIdle()
            screenshot("settings-landscape")
            compose.onNodeWithContentDescription("Locker").performClick()
            compose.waitForIdle()
            screenshot("locker-setup-landscape")
        } finally { resolver.delete(uri, null, null) }
    }
}
