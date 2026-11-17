package dev.anyu.tiebapure

internal fun originalImageRatio(ratio: Float): Float = if (ratio.isFinite() && ratio > 0f) ratio.coerceIn(10f / 21f, 21f / 10f) else 1f
internal fun originalImageWidthFraction(ratio: Float): Float = (0.7f * originalImageRatio(ratio)).coerceAtMost(1f)
