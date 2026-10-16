package com.arslandaim.omegaplayer.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.feature.locker.LockerPinPanel
import com.arslandaim.omegaplayer.ui.feature.locker.LockerPinInput
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class LandscapeLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun show(width: Int, height: Int, fontScale: Float = 1f) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))
                then DeviceConfigurationOverride.FontScale(fontScale)) {
                MaterialTheme { Surface { LockerPinPanel(2, {}, {}) } }
            }
        }
    }

    private fun assertKeysVisible() {
        // Wait for the new Activity's Compose root after native/virtual-display tests.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("1", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        (0..9).forEach { compose.onNodeWithText(it.toString(), useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithContentDescription("Delete last digit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unlock").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingerprint unlock").assertIsDisplayed()
    }

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

    @Test fun thorLandscapeShowsEveryKey() {
        show(800, 360)
        assertKeysVisible()
        screenshot("pin-landscape")
    }

    @Test fun landscapeWithLargerTextShowsEveryKey() {
        show(800, 360, 1.3f)
        assertKeysVisible()
        screenshot("pin-landscape-large-text")
    }

    @Test fun portraitKeepsEveryKeyVisible() {
        show(360, 720)
        assertKeysVisible()
        screenshot("pin-portrait")
    }

    @Test fun keyActionsPreserveTheirOrder() {
        val keys = mutableListOf<String>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(800.dp, 360.dp))) {
                MaterialTheme { Surface { LockerPinPanel(0, keys::add, {}) } }
            }
        }
        listOf("1", "2").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithContentDescription("Delete last digit").performClick()
        compose.onNodeWithText("0").performClick()
        compose.onNodeWithContentDescription("Unlock").performClick()
        assertEquals(listOf("1", "2", "Back", "0", "OK"), keys)
    }
    @Test fun deleteUpdatesDotsWhileVerificationIsPending() {
        val result = CompletableDeferred<Boolean>()
        var attempts = 0
        var unlocked = false
        compose.setContent {
            MaterialTheme { Surface {
                LockerPinInput(verify = { attempts++; result.await() },
                    onUnlocked = { unlocked = true }, onIncorrect = {}, onBiometric = {})
            } }
        }
        listOf("1", "2", "3", "4").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitUntil { attempts == 1 }
        compose.onNode(hasStateDescription("4 of 4 digits entered")).assertExists()
        compose.onNodeWithContentDescription("Delete last digit").performClick()
        compose.onNode(hasStateDescription("3 of 4 digits entered")).assertExists()
        compose.runOnIdle { result.complete(true) }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(unlocked)
        repeat(3) { compose.onNodeWithContentDescription("Delete last digit").performClick() }
        compose.onNode(hasStateDescription("0 of 4 digits entered")).assertExists()
        compose.onNodeWithText("5").performClick()
        compose.onNode(hasStateDescription("1 of 4 digits entered")).assertExists()
    }

}
