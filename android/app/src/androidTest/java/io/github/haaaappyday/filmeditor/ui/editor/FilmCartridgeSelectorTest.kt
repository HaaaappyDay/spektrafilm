package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.core.FilmPreset
import io.github.haaaappyday.filmeditor.render.ShippedFilmCatalog
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class FilmCartridgeSelectorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allStocksHaveCartridgeIconsWithoutAnImportedPhotoAndRemainSelectable() {
        val films = ShippedFilmCatalog.displayNames.map { (id, name) -> FilmPreset(id, name, id, "0".repeat(64)) }
        var selected by mutableStateOf(films.first().id)
        var calls = 0
        compose.setContent { FilmEditorTheme { PresetSelector(films, selected, true, { selected = it; calls++ }) } }
        for (film in films) {
            assertNotEquals("FILM", cartridgeArt(film.id).name)
            compose.onNodeWithTag("stock-icon-${film.id}", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("preset-${film.id}").performScrollTo()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .assertTextContains(film.displayName).performClick().assertIsSelected()
        }
        compose.runOnIdle { assertEquals(films.size, calls); assertEquals(films.last().id, selected) }
    }
}
