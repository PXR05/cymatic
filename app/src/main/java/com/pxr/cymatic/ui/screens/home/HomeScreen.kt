package com.pxr.cymatic.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.common.NavigationItem
import com.pxr.cymatic.ui.components.common.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val items = listOf(
        NavigationItem("All Songs") { navController.navigate("all_songs") },
        NavigationItem("Artists") { navController.navigate("artists") },
        NavigationItem("Albums") { navController.navigate("albums") },
        NavigationItem("Playlists") { navController.navigate("playlists") },
        NavigationItem("Settings") { navController.navigate("settings") }
    )
    val fontFamily = FontFamily(Font(R.font.pixel))

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(24.dp, 16.dp)
            ) {
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(end = 24.dp)
                )
                Text(
                    text = "Cymatic",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            NavigationList(
                items = items,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


