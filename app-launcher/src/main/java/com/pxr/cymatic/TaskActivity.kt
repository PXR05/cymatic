package com.pxr.cymatic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.theme.CymaticTheme

abstract class TaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            CymaticTheme {
                CompositionLocalProvider(
                    LocalNavController provides navController
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    protected abstract fun AppContent()
}
