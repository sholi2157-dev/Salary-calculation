package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkIconFamilyTest {
    @Test fun actionsAndStatusUseOneVectorFamilyAndDirectionalArrowsMirror() {
        for (icon in listOf(Icons.Outlined.CheckCircle, Icons.Outlined.DeleteOutline,
            Icons.Outlined.Groups, Icons.Outlined.Notifications, Icons.AutoMirrored.Outlined.ArrowForward)) {
            assertEquals(24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
            assertTrue("unexpected icon family: ${icon.name}", icon.name.substringBeforeLast('.') in setOf("Outlined", "AutoMirrored.Outlined"))
        }
        assertTrue(Icons.AutoMirrored.Outlined.ArrowForward.autoMirror)
        val kis = listOf(File("src/main/java/com/example/ui/kis"), File("app/src/main/java/com/example/ui/kis")).first { it.isDirectory }
        val mixed = kis.listFiles()!!.filter { it.extension == "kt" && Regex("Icons\\.(?:AutoMirrored\\.)?(?:Rounded|Filled|Sharp|TwoTone)\\.").containsMatchIn(it.readText()) }
        assertTrue("mixed icon families: $mixed", mixed.isEmpty())
    }
}
