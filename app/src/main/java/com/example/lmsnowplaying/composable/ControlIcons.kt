package com.example.lmsnowplaying.composable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Shuffle icons that the built-in icon set doesn't have, drawn after the
// Material skin's: "not shuffled" (two parallel arrows) and "shuffle
// releases" (crossed arrows with a record at the tail of one of them).

private fun ImageVector.Builder.path(d: String, fillType: PathFillType = PathFillType.NonZero): ImageVector.Builder =
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        pathFillType = fillType,
        fill = SolidColor(Color.Black),
    )

// The repeat loop with a diagonal slash through it: no repeat.
val RepeatOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RepeatOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
        .path("M4.2,2.8L21.2,19.8 19.8,21.2 2.8,4.2z")
        .build()
}

// Two stacked arrows pointing right: songs play in order.
val ShuffleOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("M16.01,6H4v2h12.01v3L20,7l-3.99,-4v3z")
        .path("M16.01,16H4v2h12.01v3L20,17l-3.99,-4v3z")
        .build()
}

// Crossed shuffle arrows, with a record (ring and dot) in place of the
// tail of the up-right arrow: whole releases are shuffled.
val ShuffleReleasesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShuffleReleases",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        // arrow from top-left down to bottom-right
        .path("M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z")
        // shortened arrow from the record up to top-right
        .path("M14.5,4l2.04,2.04L9.5,13.08 10.91,14.5 17.96,7.46 20,9.5L20,4h-5.5z")
        // the record: ring (even-odd) and centre dot
        .path("M7.9,13.1a3,3 0 1,0 0.01,0zM7.9,14.1a2,2 0 1,0 0.01,0z", PathFillType.EvenOdd)
        .path("M7.9,15.1a1,1 0 1,0 0.01,0z")
        .build()
}

// Material skin's info icon ("mai": circle with an i and a music note), outline and filled
// (shown while the info view is open). From the Material skin, MIT license.
val InfoOverviewIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InfoOverview",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("m11 8h2v-2h-2m0 10h2v-6h-2zm11-5v2.2h-2.2857v6.05c0 1.518-1.28 2.75-2.8571 2.75s-2.8571-1.232-2.8571-2.75 1.28-2.75 2.8571-2.75c0.41143 0 0.78857 0.077 1.1429 0.231v-5.731zm-9.2544 7.7939c-0.36354 0.06334-0.73776 0.095-1.1227 0.095-4.1379 0-7.4845-3.3039-7.4845-7.3889s3.3467-7.3889 7.4845-7.3889c3.3894 0 6.2549 2.2272 7.1745 5.2778h2.2026c-0.33146-1.4144-0.97299-2.7128-1.8604-3.8106l0.0023 0.00149c-0.4812-0.53834-0.88737-0.9658-1.4327-1.4302l-0.07723-0.038464c-1.6573-1.33-3.7423-2.1111-6.009-2.1111-5.314 0-9.623 4.2539-9.623 9.5 0 5.2461 4.3089 9.5 9.623 9.5 0.44907 0 0.88745-0.04222 1.3258-0.095-0.16038-0.475-0.25661-0.96056-0.25661-1.4883 0-0.21111 0.03207-0.41167 0.05346-0.62278")
        .build()
}

val InfoOverviewFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "InfoOverviewFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path("m22 11v2.2h-2.2857v6.05c0 1.518-1.28 2.75-2.8571 2.75s-2.8571-1.232-2.8571-2.75 1.28-2.75 2.8571-2.75c0.41143 0 0.78857 0.077 1.1429 0.231v-5.731zm-10.377-9c-5.314 0-9.623 4.2539-9.623 9.5 0 5.2461 4.309 9.5 9.623 9.5 0.44907 0 0.88779-0.04292 1.3262-0.0957-0.16038-0.475-0.25781-0.9605-0.25781-1.4883 0-2.2589 1.6038-4.1473 3.7422-4.6328v-5.3945h4.5664c-0.33124-1.4135-0.97281-2.7113-1.8594-3.8086l-2e-3 -0.00195c-0.4805-0.53746-0.97098-1.0049-1.5156-1.4688l0.0098 0.00195c-1.6573-1.33-3.7431-2.1113-6.0098-2.1113zm-0.62305 4h2v2h-2zm0 4h2v6h-2z")
        .build()
}
