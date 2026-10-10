package com.example.githubwidget.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.githubwidget.design.NumberFont

/** Compose font family for big numbers. Built once per choice and reused. */
fun numberFontFamily(context: Context, font: NumberFont): FontFamily {
    val asset = font.asset ?: return FontFamily.SansSerif
    val assets = context.assets
    fun face(weight: FontWeight) = Font(
        path = asset,
        assetManager = assets,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
    return FontFamily(
        face(FontWeight.Medium),
        face(FontWeight.SemiBold),
        face(FontWeight.Bold),
    )
}
