package com.example.voiceagent.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager

class ScreenCapture private constructor(
    private val projection: MediaProjection,
    width: Int,
    height: Int,
    dpi: Int,
    rotation: Int
) {
    @Volatile
    private var frame: Bitmap? = null

    private val reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        virtualDisplay = projection.createVirtualDisplay(
            "VoiceAgentCapture",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = img.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * img.width
                val raw = Bitmap.createBitmap(
                    img.width + rowPadding / pixelStride,
                    img.height,
                    Bitmap.Config.ARGB_8888
                )
                raw.copyPixelsFromBuffer(plane.buffer)
                val cropped = if (rowPadding == 0) raw
                else Bitmap.createBitmap(raw, 0, 0, img.width, img.height)
                frame = rotateToNatural(cropped, rotation)
                if (cropped !== raw) raw.recycle()
            } catch (t: Throwable) {
            } finally {
                img.close()
            }
        }, handler)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                close()
            }
        }, handler)
    }

    fun lastFrame(): Bitmap? = frame

    fun close() {
        try { reader.setOnImageAvailableListener(null, null) } catch (t: Throwable) {}
        try { virtualDisplay?.release() } catch (t: Throwable) {}
        virtualDisplay = null
        try { reader.close() } catch (t: Throwable) {}
        try { projection.stop() } catch (t: Throwable) {}
        if (instance === this) instance = null
    }

    private fun rotateToNatural(src: Bitmap, rotation: Int): Bitmap {
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> return src
        }
        val m = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    companion object {
        @Volatile
        var instance: ScreenCapture? = null
            private set

        fun start(ctx: Context, resultCode: Int, data: Intent): Boolean {
            instance?.close()
            return try {
                val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mpm.getMediaProjection(resultCode, data) ?: return false
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val bounds = wm.maximumWindowMetrics.bounds
                @Suppress("DEPRECATION")
                val rotation = wm.defaultDisplay.rotation
                val dpi = ctx.resources.displayMetrics.densityDpi
                instance = ScreenCapture(projection, bounds.width(), bounds.height(), dpi, rotation)
                true
            } catch (t: Throwable) {
                ServiceBridge.tryEmit("[截图] 启动录屏失败：" + t.message)
                false
            }
        }
    }
}
