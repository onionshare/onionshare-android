package org.onionshare.android.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

fun Modifier.shadow(
    shadow: Shadow,
    shape: Shape = RectangleShape
) = this.then(
    ShadowModifier(shadow, shape)
)

private class ShadowModifier(
    val shadow: Shadow,
    val shape: Shape
) : DrawModifier {

    override fun ContentDrawScope.draw() {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = shadow.color
                asFrameworkPaint().apply {
                    maskFilter = BlurMaskFilter(
                        convertRadiusToSigma(shadow.blurRadius),
                        BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            shape.createOutline(
                size, layoutDirection, this
            ).let { outline ->
                canvas.drawWithOffset(shadow.offset) {
                    when (outline) {
                        is Outline.Rectangle -> {
                            drawRect(outline.rect, paint)
                        }
                        is Outline.Rounded -> {
                            drawPath(
                                Path().apply { addRoundRect(outline.roundRect) },
                                paint
                            )
                        }
                        is Outline.Generic -> {
                            drawPath(outline.path, paint)
                        }
                    }
                }
            }
        }
        drawContent()
    }

    private fun convertRadiusToSigma(
        radius: Float,
        enable: Boolean = true
    ): Float = if (enable) {
        (radius * 0.57735 + 0.5).toFloat()
    } else {
        radius
    }

    private fun Canvas.drawWithOffset(
        offset: Offset,
        block: Canvas.() -> Unit
    ) {
        save()
        translate(offset.x, offset.y)
        block()
        restore()
    }
}