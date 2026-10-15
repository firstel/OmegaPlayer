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
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmegaPlayerLayout")
            })!!
        resolver.openOutputStream(uri)!!.use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
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
            compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
            compose.waitForIdle()
            screenshot("settings-landscape")
            compose.onNodeWithContentDescription("Locker", useUnmergedTree = true).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Next: recovery").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("4-digit PIN").assertExists()
            compose.onNodeWithText("Confirm PIN").performTextInput("1234")
            // The first field label is localized; select the two editable PIN fields in order.
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("1234")
            compose.onNodeWithText("Next: recovery").assertIsEnabled()
            screenshot("locker-setup-landscape")
            compose.onNodeWithText("Next: recovery").performClick()
            compose.waitUntil(5_000) { compose.onNodeWithText("Create vault").isDisplayed() }
            screenshot("locker-recovery-landscape")
            compose.onNodeWithText("Create vault").assertIsDisplayed().assertIsNotEnabled()
        } finally { resolver.delete(uri, null, null) }
    }
}
