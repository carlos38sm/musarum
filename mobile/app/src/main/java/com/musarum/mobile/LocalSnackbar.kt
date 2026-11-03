package com.musarum.mobile

import androidx.compose.runtime.compositionLocalOf

val LocalSnackbar = compositionLocalOf<(String) -> Unit> { { } }
