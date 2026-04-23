package com.privatevpn.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.privatevpn.app.ui.theme.AppElevation
import com.privatevpn.app.ui.theme.AppSpacing

enum class SectionTone {
    Primary,
    Secondary
}

@Composable
fun AppSection(
    modifier: Modifier = Modifier,
    tone: SectionTone = SectionTone.Primary,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppSpacing.sm),
    content: @Composable () -> Unit
) {
    val containerColor = when (tone) {
        SectionTone.Primary -> MaterialTheme.colorScheme.surface
        SectionTone.Secondary -> MaterialTheme.colorScheme.surfaceVariant
    }
    val border = when (tone) {
        SectionTone.Primary -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        SectionTone.Secondary -> null
    }
    val shadow = when (tone) {
        SectionTone.Primary -> AppElevation.sm
        SectionTone.Secondary -> AppElevation.none
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = border,
        tonalElevation = AppElevation.none,
        shadowElevation = shadow
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    }
}

@Composable
fun InlineStatusLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        androidx.compose.material3.Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

fun Modifier.softClickable(
    enabled: Boolean = true,
    shape: Shape? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val rippleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val resolvedShape = shape ?: MaterialTheme.shapes.small
    this
        .clip(resolvedShape)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = rememberRipple(bounded = true, color = rippleColor),
            onClick = onClick
        )
}
