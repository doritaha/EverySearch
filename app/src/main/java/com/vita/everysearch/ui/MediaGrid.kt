package com.vita.everysearch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vita.everysearch.data.Category
import com.vita.everysearch.data.FileEntry

/** Gallery-style grid of photo / video thumbnails. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<FileEntry>,
    state: LazyGridState,
    onClick: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items) { e ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(e.category.color().copy(alpha = 0.14f))
                    .combinedClickable(onClick = { onClick(e) }, onLongClick = { onLongClick(e) }),
            ) {
                // Fallback icon sits under the thumbnail (visible while loading or for root-only files).
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(e.category.icon(), null, Modifier.size(36.dp), tint = e.category.color())
                }
                if (e.hasPreview) MediaThumb(e, 360, Modifier.matchParentSize())
                if (e.category == Category.VIDEO) PlayBadge(Modifier.align(Alignment.Center), 20)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        e.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.merge(TextStyle(textDirection = TextDirection.Ltr)),
                    )
                    Text(
                        formatSize(e.size), color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
