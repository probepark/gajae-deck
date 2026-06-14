package io.devnogari.gajaedeck.theme

import io.devnogari.gajaedeck.resources.Res
import io.devnogari.gajaedeck.resources.app_name
import io.devnogari.gajaedeck.resources.command_palette
import io.devnogari.gajaedeck.resources.session_title
import io.devnogari.gajaedeck.resources.web_lower_assurance_notice
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalResourceApi::class)
class I18nResourcesTest {

    @Test
    fun stringResourcesResolveToNonBlankCopy() = runTest {
        listOf(
            getString(Res.string.app_name),
            getString(Res.string.session_title),
            getString(Res.string.command_palette),
            getString(Res.string.web_lower_assurance_notice),
        ).forEach { assertTrue(it.isNotBlank(), "i18n lookup returned blank") }
    }

    @Test
    fun oflLicenseNoticeIsBundledWithTheFont() = runTest {
        val ofl = Res.readBytes("files/notosanskr_OFL.txt").decodeToString()
        assertTrue(ofl.contains("SIL Open Font License"), "bundled OFL notice missing license text")
    }
}
