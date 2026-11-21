package io.github.dryearth.Nanaya.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun ToastEffect(text: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(text) {
        if (!text.isNullOrBlank()) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
}
