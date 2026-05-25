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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val items = listOf(
        NavigationItem(
            label = "All Songs",
            onClick = { navController.navigate("all_songs") }
        ),
        NavigationItem(
            label = "Artists",
            onClick = { navController.navigate("artists") }
        ),
        NavigationItem(
            label = "Albums",
            onClick = { navController.navigate("albums") }
        ),
        NavigationItem(
            label = "Playlists",
            onClick = { navController.navigate("playlists") }
        ),
        NavigationItem(
            label = "Settings",
            onClick = { navController.navigate("settings") }
        )
    )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp, 16.dp)
            ) {
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(end = 24.dp)
                )
                Text(
                    text = "Cymatic",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
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
