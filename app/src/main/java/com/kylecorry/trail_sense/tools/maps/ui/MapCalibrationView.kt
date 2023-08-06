package com.kylecorry.trail_sense.tools.maps.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import com.kylecorry.andromeda.canvas.TextMode
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.tools.maps.domain.PercentCoordinate
import com.kylecorry.trail_sense.tools.maps.domain.PhotoMap

class MapCalibrationView : BasePhotoMapView {

    var onMapClick: ((percent: PercentCoordinate) -> Unit)? = null

    var highlightedIndex: Int = 0
        set(value) {
            if (field != value) {
                movePending = true
            }
            field = value
            invalidate()
        }

    private var movePending = true

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun postDraw() {
        super.postDraw()
        drawCalibrationPoints()
        invalidate()
    }

    override fun showMap(map: PhotoMap) {
        // TODO: Maybe force rotation to remain the same?
        // TODO: This isn't working when the map rotation is not divisible by 90 and it changes
        super.showMap(map)//.copy(calibration = map.calibration.copy(rotation = map.baseRotation().toFloat())))
    }

    override fun onSinglePress(e: MotionEvent) {
        super.onSinglePress(e)
        val pixel = toSource(e.x, e.y, true)

        pixel?.let {
            val percentX = it.x / imageWidth
            val percentY = it.y / imageHeight
            val percent = PercentCoordinate(percentX, percentY)
            onMapClick?.invoke(percent.rotate(-orientation))
        }
    }

    // TODO: Support dragging the coordinates

    private fun drawCalibrationPoints() {
        var calibrationPoints =
            (map?.calibration?.calibrationPoints ?: emptyList()).mapIndexed { index, point ->
                index to point
            }

        // Sort highlighted last
        calibrationPoints = calibrationPoints.sortedBy { it.first == highlightedIndex }

        for ((i, point) in calibrationPoints) {
            val sourceCoord = point.imageLocation.rotate(orientation).toPixels(imageWidth, imageHeight)
            if (movePending && i == highlightedIndex) {
                moveTo(sourceCoord.x, sourceCoord.y)
                movePending = false
            }
            val coord = toView(sourceCoord.x, sourceCoord.y) ?: continue
            drawer.stroke(Color.WHITE)
            if (i == highlightedIndex) {
                drawer.fill(AppColor.Orange.color)
            } else {
                drawer.fill(Color.BLACK)
            }
            drawer.strokeWeight(drawer.dp(1f) / layerScale)
            drawer.circle(coord.x, coord.y, drawer.dp(12f) / layerScale)

            drawer.textMode(TextMode.Center)
            if (i == highlightedIndex) {
                drawer.fill(Color.BLACK)
            } else {
                drawer.fill(Color.WHITE)
            }
            drawer.noStroke()
            drawer.textSize(drawer.dp(10f) / layerScale)
            drawer.text((i + 1).toString(), coord.x, coord.y)
        }
    }
}