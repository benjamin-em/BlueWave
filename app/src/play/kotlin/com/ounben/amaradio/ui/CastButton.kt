package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ounben.amaradio.cast.CastManager

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    IconButton(
        onClick = {
            (context as? FragmentActivity)?.let { activity ->
                val manager = CastManager.getInstance(activity)
                manager?.openCastDialog(activity)
            }
        },
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Cast,
            contentDescription = "Cast"
        )
    }
}
