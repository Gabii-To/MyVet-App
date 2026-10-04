package com.application.myvet

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.application.myvet.presentation.dashboard.DashboardScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        DashboardScreen()
    }
}