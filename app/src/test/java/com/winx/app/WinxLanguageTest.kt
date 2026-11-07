package com.winx.app

import com.winx.app.utils.translate
import com.winx.app.utils.translateFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class WinxLanguageTest {

    @Test
    fun zuluEntrySaveLabel() {
        assertEquals(
            "Gcina Okufakiwe",
            translate("Save Entry", "isiZulu (Zulu)")
        )
    }

    @Test
    fun afrikaansBiometricLabel() {
        assertEquals(
            "Ontsluit met biometrie",
            translate("Unlock with biometrics", "Afrikaans")
        )
    }

    @Test
    fun unknownLanguageKeepsEnglish() {
        assertEquals(
            "Settings",
            translate("Settings", "unknown")
        )
    }

    @Test
    fun unknownTextIsPreserved() {
        assertEquals(
            "My own journal title",
            translate("My own journal title", "Afrikaans")
        )
    }

    @Test
    fun ratingUsesTranslatedTemplate() {
        assertEquals(
            "Gradering 4",
            translateFormat("Rating {0}", "Afrikaans", 4)
        )
    }

    @Test
    fun placeholderLikeUserDataIsNotSubstitutedAgain() {
        assertEquals(
            "Gradering {1}",
            translateFormat("Rating {0}", "Afrikaans", "{1}")
        )
    }

    @Test
    fun languageTagAliasesAreSupported() {
        assertEquals(
            "Izilungiselelo",
            translate("Settings", "zu-ZA")
        )

        assertEquals(
            "Instellings",
            translate("Settings", "af-ZA")
        )
    }
}