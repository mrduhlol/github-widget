package com.example.githubwidget.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons not in material-icons-core, drawn on the standard 24×24 grid. */
object AppIcons {

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    /** Four rounded squares — the "Widget" tab. */
    val Widget: ImageVector by lazy {
        icon(
            "Widget",
            "M5,3h4a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-4a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M15,3h4a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-4a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M5,13h4a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-4a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M15,13h4a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-4a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
        )
    }

    /** Rising bars — the "Stats" tab. */
    val Stats: ImageVector by lazy {
        icon(
            "Stats",
            "M5,13h1a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-1a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M11.5,8h1a2,2 0 0 1 2,2v9a2,2 0 0 1 -2,2h-1a2,2 0 0 1 -2,-2v-9a2,2 0 0 1 2,-2z",
            "M18,3h1a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-1a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
        )
    }

    val Flame: ImageVector by lazy {
        icon(
            "Flame",
            "M19.48,12.35c-1.57,-4.08 -7.16,-4.3 -5.81,-10.23c0.1,-0.44 -0.37,-0.78 -0.75,-0.55C9.29,3.71 6.68,8 8.87,13.62c0.18,0.46 -0.36,0.89 -0.75,0.59c-1.81,-1.37 -2,-3.34 -1.84,-4.75c0.06,-0.52 -0.62,-0.77 -0.91,-0.34C4.69,10.16 4,11.84 4,14.37c0.38,5.6 5.11,7.32 6.81,7.54c2.43,0.31 5.06,-0.14 6.95,-1.87C19.84,18.11 20.6,15.03 19.48,12.35z",
        )
    }

    /** Small contribution grid, used as a decorative mark. */
    val Graph: ImageVector by lazy {
        val cells = buildList {
            for (r in 0..2) for (c in 0..2) {
                val x = 3 + c * 6.5f
                val y = 3 + r * 6.5f
                add("M${x + 1},${y}h3a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1z")
            }
        }
        icon("Graph", *cells.toTypedArray())
    }
}
