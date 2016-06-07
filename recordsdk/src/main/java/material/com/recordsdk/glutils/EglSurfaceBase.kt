/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package material.com.recordsdk.glutils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Common base class for EGL surfaces.
 *
 *
 * There can be multiple surfaces associated with a single context.
 */
open class EglSurfaceBase protected constructor(// EglBase object we're associated with.  It may be associated with multiple surfaces.
        protected var mEglCore: EglCore) {

    private var mEGLSurface = EGL14.EGL_NO_SURFACE
    /**
     * Returns the surface's width, in pixels.
     */
    var width = -1
        private set
    /**
     * Returns the surface's height, in pixels.
     */
    var height = -1
        private set

    /**
     * Creates a window surface.
     *
     *

     * @param surface May be a Surface or SurfaceTexture.
     */
    fun createWindowSurface(surface: Any) {
        if (mEGLSurface !== EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }
        mEGLSurface = mEglCore.createWindowSurface(surface)
        width = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH)
        height = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT)
        if (DEBUG) Log.v(TAG, String.format("createWindowSurface:size(%d,%d)", width, height))
    }

    /**
     * Creates an off-screen surface.
     */
    fun createOffscreenSurface(width: Int, height: Int) {
        if (mEGLSurface !== EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }
        mEGLSurface = mEglCore.createOffscreenSurface(width, height)
        this.width = width
        this.height = height
        if (DEBUG) Log.v(TAG, String.format("createOffscreenSurface:size(%d,%d)", this.width, this.height))
    }

    /**
     * Release the EGL surface.
     */
    fun releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface)
        mEGLSurface = EGL14.EGL_NO_SURFACE
        width = -1
        height = -1
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        mEglCore.makeCurrent(mEGLSurface)
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * Makes our EGL context and surface current for drawing, using the supplied surface
     * for reading.
     */
    fun makeCurrentReadFrom(readSurface: EglSurfaceBase) {
        mEglCore.makeCurrent(mEGLSurface, readSurface.mEGLSurface)
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.

     * @return false on failure
     */
    fun swapBuffers(): Boolean {
        val result = mEglCore.swapBuffers(mEGLSurface)
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed")
        }
        return result
    }

    /**
     * Sends the presentation time stamp to EGL.

     * @param nsecs Timestamp, in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        mEglCore.setPresentationTime(mEGLSurface, nsecs)
    }

    /**
     * Saves the EGL surface to a file.
     *
     *
     * Expects that this object's EGL surface is current.
     */
    @Throws(IOException::class)
    fun saveFrame(file: File, scaleFactor: Int) {
        if (!mEglCore.isCurrent(mEGLSurface)) {
            throw RuntimeException("Expected EGL context/surface is not current")
        }

        // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  We need an int[] filled
        // with little-endian ARGB data to feed to Bitmap.
        //
        // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
        // copying data around for a 720p frame.  It's better to do a bulk get() and then
        // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
        // for a trivial frame.)
        //
        // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
        // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
        // Swapping B and R gives us ARGB.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside-down relative to what appears on screen if the
        // typical GL conventions are used.

        val startTime = System.currentTimeMillis()

        val filename = file.toString()

        val buf = ByteBuffer.allocateDirect(width * height * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()

        Thread(Runnable {
            var bos: BufferedOutputStream? = null
            try {
                bos = BufferedOutputStream(FileOutputStream(filename))
                val fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                fullBitmap.copyPixelsFromBuffer(buf)
                val m = Matrix()
                m.preScale(1f, -1f)
                if (scaleFactor != 1) {
                    val scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, width / scaleFactor, height / scaleFactor, true)
                    val flippedScaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, m, true)
                    flippedScaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                    scaledBitmap.recycle()
                    flippedScaledBitmap.recycle()
                } else {
                    val flippedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width, height, m, true)
                    flippedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                }
                fullBitmap.recycle()

                Log.d(TAG, "Saved " + width / scaleFactor + "x" + height / scaleFactor + " frame as '" + filename + "' in " + (System.currentTimeMillis() - startTime) + " ms")
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } finally {
                if (bos != null)
                    try {
                        bos.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

            }
        }).start()

    }

    fun updateSize() {
        width = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH)
        height = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT)
        if (DEBUG) Log.v(TAG, String.format("updateSize:%d,%d", width, height))
    }

    companion object {
        private val DEBUG = false
        protected val TAG = "EglSurfaceBase"
    }
}