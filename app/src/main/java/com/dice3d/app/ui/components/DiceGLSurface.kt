package com.dice3d.app.ui.components

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.dice3d.app.engine.CameraController
import com.dice3d.app.engine.GLRenderer

class DiceGLSurfaceView(
    context: Context,
    private val renderer: GLRenderer,
    private val cameraController: CameraController
) : GLSurfaceView(context) {

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector: ScaleGestureDetector

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cameraController.zoom(-detector.scaleFactor + 1f)
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                lastTouchX = event.getX(idx)
                lastTouchY = event.getY(idx)
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && isDragging) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        cameraController.rotate(dx * 0.5f, dy * 0.5f)
                        lastTouchX = x
                        lastTouchY = y
                    }
                } else if (event.pointerCount == 2) {
                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                    if (lastTwoFingerDist > 0) {
                        val scale = dist / lastTwoFingerDist
                        cameraController.zoom(1f - scale)
                        val panDx = midX - lastTwoFingerMidX
                        val panDy = midY - lastTwoFingerMidY
                        cameraController.pan(panDx * 0.01f, panDy * 0.01f, 0f)
                    }
                    lastTwoFingerDist = dist
                    lastTwoFingerMidX = midX
                    lastTwoFingerMidY = midY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                lastTwoFingerDist = 0f
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val ptrIdx = event.actionIndex
                val ptrId = event.getPointerId(ptrIdx)
                if (ptrId == activePointerId) {
                    val newIdx = if (ptrIdx == 0) 1 else 0
                    if (newIdx < event.pointerCount) {
                        activePointerId = event.getPointerId(newIdx)
                        lastTouchX = event.getX(newIdx)
                        lastTouchY = event.getY(newIdx)
                    }
                }
                lastTwoFingerDist = 0f
            }
        }
        return true
    }

    private var lastTwoFingerDist = 0f
    private var lastTwoFingerMidX = 0f
    private var lastTwoFingerMidY = 0f
}
