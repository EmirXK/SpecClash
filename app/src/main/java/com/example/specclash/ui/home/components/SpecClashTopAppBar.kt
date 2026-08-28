package com.example.specclash.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.specclash.BuildConfig

/**
 * Top app bar with:
 *  * a navigation icon (hamburger menu) that opens the side drawer,
 *  * the SpecClash title + build version,
 *  * a bookmark / pin icon that toggles the active matchup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecClashTopAppBar(
    onNavigationIconClick: () -> Unit,
    onPinMatchupClick: () -> Unit,
    isCurrentMatchupPinned: Boolean,
    isBackNavigation: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val canPin = !isBackNavigation
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = "SpecClash",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = if (isBackNavigation) (onBackClick ?: onNavigationIconClick) else onNavigationIconClick) {
                Icon(
                    imageVector = if (isBackNavigation) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu,
                    contentDescription = if (isBackNavigation) "Back" else "Open menu",
                )
            }
        },
        actions = {
            if (canPin) {
                IconButton(onClick = onPinMatchupClick) {
                    Icon(
                        imageVector = if (isCurrentMatchupPinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isCurrentMatchupPinned) "Unpin matchup" else "Pin matchup",
                        tint = if (isCurrentMatchupPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
