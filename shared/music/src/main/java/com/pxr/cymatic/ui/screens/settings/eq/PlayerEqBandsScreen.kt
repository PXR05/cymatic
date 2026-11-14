package com.pxr.cymatic.ui.screens.settings.eq

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.ui.components.eq.EqBandRow
import com.pxr.cymatic.ui.components.screen.BaseScreen

@Composable
internal fun PlayerEqBandsScreen(
    preset: EqPreset,
    viewModel: EqViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    var expandedBandIndex by remember { mutableIntStateOf(-1) }
    BackHandler(onBack = onBack)
    BaseScreen("Bands", onBack, modifier = modifier) {
        Column(Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp, 16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                preset.bands.forEachIndexed { index, band ->
                    EqBandRow(
                        index = index,
                        band = band,
                        isExpanded = expandedBandIndex == index,
                        onToggleExpand = {
                            expandedBandIndex = if (expandedBandIndex == index) -1 else index
                        },
                        onToggleEnabled = {
                            viewModel.updateBand(index, band.copy(enabled = !band.enabled))
                        },
                        onBandChange = { updated -> viewModel.updateBand(index, updated) },
                        onRemove = { viewModel.removeBand(index) },
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .border(1.dp, MaterialTheme.colorScheme.secondary)
                    )
                }

                if (preset.bands.size < EqViewModel.MAX_BANDS) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable(
                                    onClick = { viewModel.addBand() },
                                    indication = null,
                                    interactionSource = null,
                                ),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "+ Add Band",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}
