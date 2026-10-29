package com.ounben.amaradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TabHeader(titleRes: Int) {
    Text(
        text = stringResource(id = titleRes),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun PillTabRow(
    selectedIndex: Int,
    titles: List<String>,
    onSelect: (Int) -> Unit
) {
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (titles.size - 1).coerceAtLeast(0)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        edgePadding = 12.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicator = {},
        divider = {}
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Tab(
                selected = selected,
                modifier = Modifier
                    .padding(horizontal = 3.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    ),
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Composable
fun SingleTabContainer(
    titleRes: Int,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabHeader(titleRes = titleRes)
        content()
    }
}
