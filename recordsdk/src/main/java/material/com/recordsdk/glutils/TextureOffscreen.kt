package material.com.recordsdk.glutils

/*
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: TextureOffscreen.java
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
*/

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log

/**
 * Offscreen class with backing texture using FBO to draw using OpenGL|ES into the texture
 */
class TextureOffscreen {

    private val TEX_TARGET = GLES20.GL_TEXTURE_2D
    private val mHasDepthBuffer: Boolean
    /**
     * get dimension(width) of this offscreen
     * @return
     */
    var width: Int = 0
        private set
    /**
     * get dimension(height) of this offscreen
     * @return
     */
    var height: Int = 0
        private set                            // dimension of drawing area of this offscreen
    /**
     * get backing texture dimension(width) of this offscreen
     * @return
     */
    var texWidth: Int = 0
        private set
    /**
     * get backing texture dimension(height) of this offscreen
     * @return
     */
    var texHeight: Int = 0
        private set                        // actual texture size
    /**
     * get backing texture id for this offscreen
     * you can use this texture id to draw other render buffer with OpenGL|ES
     * @return
     */
    var texture = -1
        private set                            // backing texture id
    private var mDepthBufferObj = -1
    private var mFrameBufferObj = -1    // buffer object ids for offscreen
    /**
     * get internal texture matrix
     * @return
     */
    val rawTexMatrix = FloatArray(16)        // texture matrix

    @JvmOverloads constructor(width: Int, height: Int, use_depth_buffer: Boolean, adjust_power2: Boolean = false) {
        if (DEBUG) Log.v(TAG, "Constructor")
        this.width = width
        this.height = height
        mHasDepthBuffer = use_depth_buffer
        prepareFramebuffer(width, height, adjust_power2)
    }

    /**
     * wrap a existing texture as TextureOffscreen
     * @param tex_id
     * *
     * @param width
     * *
     * @param height
     * *
     * @param use_depth_buffer
     * *
     * @param adjust_power2
     */
    @JvmOverloads constructor(tex_id: Int, width: Int, height: Int,
                              use_depth_buffer: Boolean, adjust_power2: Boolean = false) {
        if (DEBUG) Log.v(TAG, "Constructor")
        this.width = width
        this.height = height
        mHasDepthBuffer = use_depth_buffer

        createFrameBuffer(width, height, adjust_power2)
        assignTexture(tex_id, width, height)
    }

    /**
     * release related resources
     */
    fun release() {
        if (DEBUG) Log.v(TAG, "release")
        releaseFrameBuffer()
    }

    /**
     * switch to rendering buffer to draw
     * viewport of OpenGL|ES is automatically changed
     * and you will apply your own viewport after calling #unbind.
     */
    fun bind() {
        //		if (DEBUG) Log.v(TAG, "bind:");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj)
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * return to default frame buffer
     */
    fun unbind() {
        //		if (DEBUG) Log.v(TAG, "unbind:");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private val mResultMatrix = FloatArray(16)
    /**
     * get copy of texture matrix
     * @return
     */
    val texMatrix: FloatArray
        get() {
            System.arraycopy(rawTexMatrix, 0, mResultMatrix, 0, 16)
            return mResultMatrix
        }

    /**
     * get copy of texture matrix
     * you should allocate array at least 16 of float
     * @param matrix
     */
    fun getTexMatrix(matrix: FloatArray, offset: Int) {
        System.arraycopy(rawTexMatrix, 0, matrix, offset, rawTexMatrix.size)
    }

    fun assignTexture(texture_id: Int, width: Int, height: Int) {
        if (width > texWidth || height > texHeight) {
            val adjust_power2 = texWidth == this.width && texHeight == this.height
            this.width = width
            this.height = height
            releaseFrameBuffer()
            createFrameBuffer(width, height, adjust_power2)
        }
        texture = texture_id
        // bind frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj)
        GlUtil.checkGlError("glBindFramebuffer " + mFrameBufferObj)
        // connect color buffer(backing texture) to frame buffer object as a color buffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                TEX_TARGET, texture, 0)
        GlUtil.checkGlError("glFramebufferTexture2D")

        if (mHasDepthBuffer) {
            // connect depth buffer to frame buffer object
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, mDepthBufferObj)
            GlUtil.checkGlError("glFramebufferRenderbuffer")
        }

        // confirm whether all process successfully completed.
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete, status=" + status)
        }

        // reset to default frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // initialize texture matrix
        Matrix.setIdentityM(rawTexMatrix, 0)
        rawTexMatrix[0] = width / texWidth.toFloat()
        rawTexMatrix[5] = height / texHeight.toFloat()
    }

    fun loadBitmap(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        if (width > texWidth || height > texHeight) {
            val adjust_power2 = texWidth == this.width && texHeight == this.height
            this.width = width
            this.height = height
            releaseFrameBuffer()
            createFrameBuffer(width, height, adjust_power2)
        }
        GLES20.glBindTexture(TEX_TARGET, texture)
        GLUtils.texImage2D(TEX_TARGET, 0, bitmap, 0)
        GLES20.glBindTexture(TEX_TARGET, 0)
        // initialize texture matrix
        Matrix.setIdentityM(rawTexMatrix, 0)
        rawTexMatrix[0] = width / texWidth.toFloat()
        rawTexMatrix[5] = height / texHeight.toFloat()
    }

    /**
     * prepare frame buffer etc. for this instance
     */
    private fun prepareFramebuffer(width: Int, height: Int, adjust_power2: Boolean) {
        GlUtil.checkGlError("prepareFramebuffer start")

        createFrameBuffer(width, height, adjust_power2)
        // make a texture id as a color buffer
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GlUtil.checkGlError("glGenTextures")

        GLES20.glBindTexture(TEX_TARGET, ids[0])
        GlUtil.checkGlError("glBindTexture " + ids[0])

        // set parameters for backing texture
        GLES20.glTexParameterf(TEX_TARGET, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(TEX_TARGET, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameteri(TEX_TARGET, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(TEX_TARGET, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GlUtil.checkGlError("glTexParameter")

        // allocate memory for texture
        GLES20.glTexImage2D(TEX_TARGET, 0, GLES20.GL_RGBA, texWidth, texHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GlUtil.checkGlError("glTexImage2D")

        assignTexture(ids[0], width, height)
    }

    private fun createFrameBuffer(width: Int, height: Int, adjust_power2: Boolean) {
        val ids = IntArray(1)

        if (adjust_power2) {
            // dimension of texture should be a power of 2
            var w = 1
            while (w < width) {
                w = w shl 1
            }
            var h = 1
            while (h < height) {
                h = h shl 1
            }
            if (texWidth != w || texHeight != h) {
                texWidth = w
                texHeight = h
            }
        } else {
            texWidth = width
            texHeight = height
        }

        if (mHasDepthBuffer) {
            // if depth buffer is required, create and initialize render buffer object
            GLES20.glGenRenderbuffers(1, ids, 0)
            mDepthBufferObj = ids[0]
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBufferObj)
            // the depth is always 16 bits
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, texWidth, texHeight)
        }
        // create and bind frame buffer object
        GLES20.glGenFramebuffers(1, ids, 0)
        GlUtil.checkGlError("glGenFramebuffers")
        mFrameBufferObj = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferObj)
        GlUtil.checkGlError("glBindFramebuffer " + mFrameBufferObj)

        // reset to default frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

    }

    /**
     * release all related resources
     */
    private fun releaseFrameBuffer() {
        val ids = IntArray(1)
        // release frame buffer object
        if (mFrameBufferObj >= 0) {
            ids[0] = mFrameBufferObj
            GLES20.glDeleteFramebuffers(1, ids, 0)
            mFrameBufferObj = -1
        }
        // release depth buffer is exists
        if (mDepthBufferObj >= 0) {
            ids[0] = mDepthBufferObj
            GLES20.glDeleteRenderbuffers(1, ids, 0)
            mDepthBufferObj = 0
        }
        // release backing texture
        if (texture >= 0) {
            ids[0] = texture
            GLES20.glDeleteTextures(1, ids, 0)
            texture = -1
        }
    }

    companion object {
        private val DEBUG = false
        private val TAG = "TextureOffscreen"
    }
}
/**
 * Constructor
 * @param width dimension of offscreen(width)
 * *
 * @param height dimension of offscreen(height)
 * *
 * @param use_depth_buffer set true if you use depth buffer. the depth is fixed as 16bits
 */
/**
 * wrap a existing texture as TextureOffscreen
 * @param tex_id
 * *
 * @param width
 * *
 * @param height
 * *
 * @param use_depth_buffer
 */
