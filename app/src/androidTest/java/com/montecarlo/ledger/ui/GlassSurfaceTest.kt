package com.montecarlo.ledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.montecarlo.ledger.AppTheme
import org.junit.Rule
import org.junit.Test

class GlassSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun solidListSurface_keepsContentReadableWithoutGlass() {
        composeRule.setContent {
            AppTheme {
                SolidListSurface {
                    Text("Routine ledger row")
                }
            }
        }

        composeRule.onNodeWithText("Routine ledger row").assertIsDisplayed()
    }

    @Test
    fun minimumIconButtonTouchTarget_isAtLeastFortyEightDp() {
        composeRule.setContent {
            AppTheme {
                IconButton(
                    onClick = {},
                    modifier = Modifier.minimumIconButtonTouchTarget(),
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Help")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Help")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
