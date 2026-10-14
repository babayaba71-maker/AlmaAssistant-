package com.example

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

enum class AssistantGesture {
  SWIPE_DOWN,  // Dismiss UI / Exit app
  SWIPE_UP,    // Clear current context / Reset
  SWIPE_LEFT,  // Toggle Hands-Free
  SWIPE_RIGHT  // Trigger Voice Command
}

class AlmaGestureDetector(
  context: Context,
  private val onGestureDetected: (AssistantGesture) -> Unit
) {
  private val swipeThreshold = 100f
  private val swipeVelocityThreshold = 140f

  private val detector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent): Boolean {
        return false // Allow default touch processing
      }

      override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
      ): Boolean {
        if (e1 == null) return false
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x

        return if (abs(diffY) > abs(diffX)) {
          if (abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
            if (diffY > 0) {
              onGestureDetected(AssistantGesture.SWIPE_DOWN)
            } else {
              onGestureDetected(AssistantGesture.SWIPE_UP)
            }
            true
          } else {
            false
          }
        } else {
          if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
            if (diffX > 0) {
              onGestureDetected(AssistantGesture.SWIPE_RIGHT)
            } else {
              onGestureDetected(AssistantGesture.SWIPE_LEFT)
            }
            true
          } else {
            false
          }
        }
      }
    }
  )

  fun onTouchEvent(event: MotionEvent): Boolean {
    return detector.onTouchEvent(event)
  }
}
