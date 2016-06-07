package material.com.recordsdk.media

/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MediaScreenEncoder.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.opengl.EGLContext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

import java.io.IOException

import material.com.recordsdk.glutils.EglTask
import material.com.recordsdk.glutils.FullFrameRect
import material.com.recordsdk.glutils.Texture2dProgram
import material.com.recordsdk.glutils.WindowSurface
import org.jetbrains.annotations.NotNull


class MediaScreenEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoder.MediaEncoderListener,
                         private var mMediaProjection: MediaProjection?, width: Int, height: Int, private val mDensity: Int) : MediaVideoEncoderBase(muxer, listener, width, height) {
    private var mSurface: Surface? = null
    private val mHandler: Handler

    private val display: VirtualDisplay? = null

    init {
        val thread = HandlerThread(TAG)
        thread.start()
        mHandler = Handler(thread.looper)
    }


    override fun release() {
        mHandler.looper.quit()
        super.release()

        if (mMediaCodec != null) {
            mMediaCodec!!.stop()
            mMediaCodec!!.release()
            mMediaCodec = null
        }
        display?.release()
        if (mMediaProjection != null) {
            mMediaProjection!!.stop()
        }
    }

    @Throws(IOException::class)
    override internal fun prepare() {
        if (DEBUG) Log.i(TAG, "prepare: ")
        mSurface = prepare_surface_encoder(MIME_TYPE, FRAME_RATE)

        //        Log.d(TAG,"created input surface: " + mSurface);
        //        display = mMediaProjection.createVirtualDisplay(
        //                "Capturing Display",
        //                mWidth, mHeight, mDensity,
        //                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        //                mSurface, null, null);
        //		if (DEBUG) Log.v(TAG,  "screen capture loop:display=" + display);

        mMediaCodec!!.start()
        mIsCapturing = true

        //		frameAvailableSoon();
        Thread(mScreenCaptureTask, "ScreenCaptureThread").start()
        Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener.onPrepared(this)
            } catch (e: Exception) {
                Log.e(TAG, "prepare:", e)
            }

        }
    }

    override fun stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording:")
        synchronized (mSync) {
            mIsCapturing = false
            mSync.notifyAll()
        }
        super.stopRecording()
    }

    private var requestDraw: Boolean = false
    private val mScreenCaptureTask = DrawTask(null, 0)

    private inner class DrawTask(shared_context: EGLContext?, flags: Int) : EglTask(shared_context, flags) {
        private var display: VirtualDisplay? = null
        private var intervals: Long = 0
        private var mTexId: Int = 0
        private var mSourceTexture: SurfaceTexture? = null
        private var mSourceSurface: Surface? = null
        private var mEncoderSurface: WindowSurface? = null
        private var mDrawer: FullFrameRect? = null
        private val mTexMatrix = FloatArray(16)

        override fun onStart() {
            if (DEBUG) Log.d(TAG, "mScreenCaptureTask#onStart:")
            mDrawer = FullFrameRect(Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
            mTexId = mDrawer!!.createTextureObject()
            mSourceTexture = SurfaceTexture(mTexId)
            mSourceTexture!!.setDefaultBufferSize(mWidth, mHeight)
            mSourceSurface = Surface(mSourceTexture)
            mSourceTexture!!.setOnFrameAvailableListener(mOnFrameAvailableListener, mHandler)
            mEncoderSurface = WindowSurface(eglCore!!, mSurface!!)


            if (DEBUG) Log.d(TAG, "setup VirtualDisplay")
            intervals = (1000f / FRAME_RATE).toLong()
            display = mMediaProjection!!.createVirtualDisplay(
                    "Capturing Display",
                    mWidth, mHeight, mDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mSourceSurface, null, null)
            if (DEBUG) Log.v(TAG, "screen capture loop:display=" + display!!)
            queueEvent(mDrawTask)
        }

        override fun onStop() {
            if (mDrawer != null) {
                mDrawer!!.release()
                mDrawer = null
            }
            if (mSourceSurface != null) {
                mSourceSurface!!.release()
                mSourceSurface = null
            }
            if (mSourceTexture != null) {
                mSourceTexture!!.release()
                mSourceTexture = null
            }
            if (mEncoderSurface != null) {
                mEncoderSurface!!.release()
                mEncoderSurface = null
            }
            makeCurrent()
            if (DEBUG) Log.v(TAG, "mScreenCaptureTask#onStop:")
            if (display != null) {
                if (DEBUG) Log.v(TAG, "release VirtualDisplay")
                display!!.release()
            }
            if (DEBUG) Log.v(TAG, "tear down MediaProjection")
            if (mMediaProjection != null) {
                mMediaProjection!!.stop()
                mMediaProjection = null
            }
        }

        override fun onError(e: Exception): Boolean {
            if (DEBUG) Log.w(TAG, "mScreenCaptureTask:", e)
            return false
        }

        override fun processRequest(request: Int, arg1: Int, arg2: Any): Boolean {
            return false
        }

        private val mOnFrameAvailableListener = OnFrameAvailableListener {
            if (mIsCapturing) {
                synchronized (mSync) {
                    requestDraw = true
                    mSync.notifyAll()
                }
            }
        }

        private val mDrawTask = object : Runnable {
            override fun run() {
                var local_request_pause: Boolean = false
                var local_request_draw: Boolean = false
                synchronized (mSync) {
                    local_request_pause = mRequestPause
                    local_request_draw = requestDraw
                    if (!requestDraw) {
                        try {
                            mSync.wait(intervals)
                            local_request_pause = mRequestPause
                            local_request_draw = requestDraw
                            requestDraw = false
                        } catch (e: InterruptedException) {
                            return
                        }

                    }
                }
                if (mIsCapturing) {
                    if (local_request_draw) {
                        mSourceTexture!!.updateTexImage()
                        mSourceTexture!!.getTransformMatrix(mTexMatrix)
                    }
                    if (!local_request_pause) {
                        mEncoderSurface!!.makeCurrent()
                        mDrawer!!.drawFrame(mTexId, mTexMatrix)
                        mEncoderSurface!!.swapBuffers()
                    }
                    makeCurrent()
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    GLES20.glFlush()
                    frameAvailableSoon()
                    queueEvent(this)
                } else {
                    releaseSelf()
                }
            }
        }
    }

    companion object {
        private val DEBUG = false    // TODO set false on release
        private val TAG = "MediaScreenEncoder"

        private val MIME_TYPE = "video/avc"
        // parameters for recording
        private val FRAME_RATE = 25
    }
}
