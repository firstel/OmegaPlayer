package com.arslandaim.omegaplayer.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.data.*
import com.arslandaim.omegaplayer.ui.feature.locker.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultDeleteTest {
    @get:Rule val compose=createComposeRule()
    @Test fun longPressRequiresConfirmationAndCancelKeepsVideo() {
        val video=LockedVideo(originalPath="original",lockerPath="/missing/test.mp4",name="Vault test",duration=1000)
        var pending by mutableStateOf(false)
        var deleted=false; var opened=false
        compose.setContent { MaterialTheme { Surface {
            VaultItem(video,onClick={ opened=true },onUnlock={},onDelete={ pending=true })
            if(pending) VaultDeleteDialog(video,onDismiss={ pending=false },onConfirm={ deleted=true; pending=false })
        } } }
        compose.onNodeWithText("Vault test").performTouchInput { longClick() }
        compose.onNodeWithText("Delete from vault?").assertIsDisplayed()
        assertFalse(deleted); assertFalse(opened)
        compose.onNodeWithText("Cancel").performClick(); assertFalse(deleted)
        compose.onNodeWithText("Vault test").performTouchInput { longClick() }
        compose.onNodeWithText("Delete",substring=false).performClick()
        assertTrue(deleted); assertFalse(opened)
    }
    @Test fun onlyConfirmedPrivateCopyIsDeletedAndOriginalIsPreserved() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LockerDatabase::class.java).build()
        val root=File(context.filesDir,"locker").apply { mkdirs() }
        val copy=File(root,"delete-test.mp4").apply { writeText("vault") }
        val original=File(context.filesDir,"original-delete-test.mp4").apply { writeText("original") }
        val dao=db.lockerDao()
        try {
            dao.insertLockedVideo(LockedVideo(originalPath=original.path,lockerPath=copy.path,name="Delete test",duration=1))
            assertTrue(deleteVaultCopy(context,dao.getAllLockedVideos().single(),dao))
            assertFalse(copy.exists()); assertTrue(original.exists()); assertTrue(dao.getAllLockedVideos().isEmpty())
            dao.insertLockedVideo(LockedVideo(originalPath=original.path,lockerPath=original.path,name="Outside vault",duration=1))
            assertFalse(deleteVaultCopy(context,dao.getAllLockedVideos().single(),dao))
            assertTrue(original.exists()); assertEquals(1,dao.getAllLockedVideos().size)
        } finally { copy.delete(); original.delete(); db.close() }
    }
}
