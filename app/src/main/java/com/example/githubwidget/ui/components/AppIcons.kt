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

    /** Arrow pointing down into a tray — downloads / app updates. */
    val Download: ImageVector by lazy {
        icon(
            "Download",
            "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
        )
    }

    /** GitHub Octocat mark — the tour mascot. */
    val Octocat: ImageVector by lazy {
        icon(
            "Octocat",
            "M12,0.297c-6.63,0 -12,5.373 -12,12 0,5.303 3.438,9.8 8.205,11.385 0.6,0.113 0.82,-0.258 0.82,-0.577 0,-0.285 -0.01,-1.04 -0.015,-2.04 -3.338,0.724 -4.042,-1.61 -4.042,-1.61C4.422,18.07 3.633,17.7 3.633,17.7c-1.087,-0.744 0.084,-0.729 0.084,-0.729 1.205,0.084 1.838,1.236 1.838,1.236 1.07,1.835 2.809,1.305 3.495,0.998 0.108,-0.776 0.417,-1.305 0.76,-1.605 -2.665,-0.3 -5.466,-1.332 -5.466,-5.93 0,-1.31 0.465,-2.38 1.235,-3.22 -0.135,-0.303 -0.54,-1.523 0.105,-3.176 0,0 1.005,-0.322 3.3,1.23 0.96,-0.267 1.98,-0.399 3,-0.405 1.02,0.006 2.04,0.138 3,0.405 2.28,-1.552 3.285,-1.23 3.285,-1.23 0.645,1.653 0.24,2.873 0.12,3.176 0.765,0.84 1.23,1.91 1.23,3.22 0,4.61 -2.805,5.625 -5.475,5.92 0.42,0.36 0.81,1.096 0.81,2.22 0,1.606 -0.015,2.896 -0.015,3.286 0,0.315 0.21,0.69 0.825,0.57C20.565,22.092 24,17.592 24,12.297c0,-6.627 -5.373,-12 -12,-12",
        )
    }

    /** Small contribution grid, used as a decorative mark. */
    val Graph: ImageVector by lazy {        val cells = buildList {
            for (r in 0..2) for (c in 0..2) {
                val x = 3 + c * 6.5f
                val y = 3 + r * 6.5f
                add("M${x + 1},${y}h3a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1z")
            }
        }
        icon("Graph", *cells.toTypedArray())
    }
}
