package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun Header(
    queueSource: String,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))
    val navController = LocalNavController.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "<",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontFamily = fontFamily,
            modifier = Modifier
                .padding(24.dp)
                .clickable(
                    onClick = { navController.popBackStack() },
                    indication = null,
                    interactionSource = null
                )
        )

        Text(
            text = queueSource,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontFamily = fontFamily
        )

        Text(
            text = "<",
            color = Color.Transparent,
            fontSize = 24.sp,
            fontFamily = fontFamily,
            modifier = Modifier
                .padding(24.dp)
        )
    }
}


