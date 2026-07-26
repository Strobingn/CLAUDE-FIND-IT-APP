package com.example.ui

import androidx.compose.runtime.Composable

/**
 * Live app root. Terrain files remain available from the Import tab without an
 * always-on action obscuring the map and other screens.
 */
@Composable
fun FindItAppRootWired(viewModel: HillshadeViewModel) {
    FindItAppRoot(viewModel = viewModel)
}
