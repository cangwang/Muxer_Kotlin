/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent

import java.nio.FloatBuffer

/**
 * This class essentially represents a viewport-sized sprite that will be rendered with
 * a texture, usually from an external source like the camera or video decoder.
 */
class FullFrameRect
/**
 * Prepares the object.

 * @param program The program to use.  FullFrameRect takes ownership, and will release
 * *                the program when no longer needed.
 */
(program: Texture2dProgram) {

    enum class SCREEN_ROTATION {
        LANDSCAPE, VERTICAL, UPSIDEDOWN_LANDSCAPE, UPSIDEDOWN_VERTICAL
    }

    private val mRectDrawable = Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE)
    /**
     * Returns the program currently in use.
     */
    var program: Texture2dProgram? = null
        private set
    private val mDrawLock = Object()

    private val mMvpMatrix = FloatArray(16)

    private var mCorrectVerticalVideo = false
    private var mScaleToFit: Boolean = false
    private var requestedOrientation = SCREEN_ROTATION.LANDSCAPE

    init {
        this.program = program
        Matrix.setIdentityM(mMvpMatrix, 0)
    }

    /**
     * Adjust the MVP Matrix to rotate and crop the texture
     * to make vertical video appear upright
     */
    fun adjustForVerticalVideo(orientation: SCREEN_ROTATION, scaleToFit: Boolean) {
        synchronized (mDrawLock) {
            mCorrectVerticalVideo = true
            mScaleToFit = scaleToFit
            requestedOrientation = orientation
            Matrix.setIdentityM(mMvpMatrix, 0)
            when (orientation) {
                FullFrameRect.SCREEN_ROTATION.VERTICAL -> if (scaleToFit) {
                    Matrix.rotateM(mMvpMatrix, 0, -90f, 0f, 0f, 1f)
                    Matrix.scaleM(mMvpMatrix, 0, 3.16f, 1.0f, 1f)
                } else {
                    Matrix.scaleM(mMvpMatrix, 0, 0.316f, 1f, 1f)
                }
                FullFrameRect.SCREEN_ROTATION.UPSIDEDOWN_LANDSCAPE -> if (scaleToFit) {
                    Matrix.rotateM(mMvpMatrix, 0, -180f, 0f, 0f, 1f)
                }
                FullFrameRect.SCREEN_ROTATION.UPSIDEDOWN_VERTICAL -> if (scaleToFit) {
                    Matrix.rotateM(mMvpMatrix, 0, 90f, 0f, 0f, 1f)
                    Matrix.scaleM(mMvpMatrix, 0, 3.16f, 1.0f, 1f)
                } else {
                    Matrix.scaleM(mMvpMatrix, 0, 0.316f, 1f, 1f)
                }
                else -> {
                }
            }
        }
    }

    fun resetMatrix() {
        Matrix.setIdentityM(mMvpMatrix, 0)
    }

    fun setMatrix(mvp_matrix: FloatArray, offset: Int): FloatArray {
        System.arraycopy(mvp_matrix, offset, mMvpMatrix, 0, 16)
        return mMvpMatrix
    }

    fun setScale(scaleX: Float, scaleY: Float) {
        mMvpMatrix[0] = scaleX
        mMvpMatrix[5] = scaleY
    }

    fun flipMatrix(verticalFlip: Boolean) {
        val mat = FloatArray(32)
        System.arraycopy(mMvpMatrix, 0, mat, 16, 16)
        Matrix.setIdentityM(mat, 0)
        if (verticalFlip) {
            Matrix.scaleM(mat, 0, 1f, -1f, 1f)
        } else {
            Matrix.scaleM(mat, 0, -1f, 1f, 1f)
        }
        Matrix.multiplyMM(mMvpMatrix, 0, mat, 0, mat, 16)
    }

    /**
     * Releases resources.
     */
    fun release() {
        if (program != null) {
            program!!.release()
            program = null
        }
    }

    /**
     * Changes the program.  The previous program will be released.
     */
    fun changeProgram(program: Texture2dProgram) {
        this.program!!.release()
        this.program = program
    }

    /**
     * Creates a texture object suitable for use with drawFrame().
     */
    fun createTextureObject(): Int {
        return program!!.createTextureObject()
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */
    fun drawFrame(textureId: Int, texMatrix: FloatArray) {
        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        synchronized (mDrawLock) {
            if (mCorrectVerticalVideo && !mScaleToFit && (requestedOrientation == SCREEN_ROTATION.VERTICAL || requestedOrientation == SCREEN_ROTATION.UPSIDEDOWN_VERTICAL)) {
                Matrix.scaleM(texMatrix, 0, 0.316f, 1.0f, 1f)
            }
            program!!.draw(mMvpMatrix, mRectDrawable.vertexArray!!, 0,
                    mRectDrawable.vertexCount, mRectDrawable.coordsPerVertex,
                    mRectDrawable.vertexStride,
                    texMatrix, TEX_COORDS_BUF, textureId, TEX_COORDS_STRIDE)
        }
    }

    /**
     * Pass touch event down to the
     * texture's shader program

     * @param ev
     */
    fun handleTouchEvent(ev: MotionEvent) {
        program!!.handleTouchEvent(ev)
    }

    /**
     * Updates the filter
     * @return the int code of the new filter
     */
    fun updateFilter(newFilter: Int) {
        val programType: Texture2dProgram.ProgramType
        var kernel: FloatArray? = null
        var colorAdj = 0.0f

        if (DEBUG) Log.d(TAG, "Updating filter to " + newFilter)
        when (newFilter) {
            FILTER_NONE -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT
            FILTER_BLACK_WHITE -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW
            FILTER_NIGHT -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_NIGHT
            FILTER_CHROMA_KEY -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_CHROMA_KEY
            FILTER_SQUEEZE -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_SQUEEZE
            FILTER_TWIRL -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_TWIRL
            FILTER_TUNNEL -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_TUNNEL
            FILTER_BULGE -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BULGE
            FILTER_DENT -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_DENT
            FILTER_FISHEYE -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FISHEYE
            FILTER_STRETCH -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_STRETCH
            FILTER_MIRROR -> programType = Texture2dProgram.ProgramType.TEXTURE_EXT_MIRROR
            FILTER_BLUR -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT3x3
                kernel = floatArrayOf(1f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 4f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 1f / 16f)
            }
            FILTER_SHARPEN -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT3x3
                kernel = floatArrayOf(0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f)
            }
            FILTER_EDGE_DETECT -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT3x3
                kernel = floatArrayOf(-1f, -1f, -1f, -1f, 8f, -1f, -1f, -1f, -1f)
            }
            FILTER_EMBOSS -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT3x3
                kernel = floatArrayOf(2f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f)
                colorAdj = 0.5f
            }
            else -> throw RuntimeException("Unknown filter mode " + newFilter)
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != program!!.programType) {
            changeProgram(Texture2dProgram(programType))
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            program!!.setKernel(kernel, colorAdj)
        }
    }

    companion object {
        private val DEBUG = false
        private val TAG = "FullFrameRect"

        val FILTER_NONE = 0
        val FILTER_BLACK_WHITE = 1
        val FILTER_NIGHT = 2
        val FILTER_CHROMA_KEY = 3
        val FILTER_BLUR = 4
        val FILTER_SHARPEN = 5
        val FILTER_EDGE_DETECT = 6
        val FILTER_EMBOSS = 7
        val FILTER_SQUEEZE = 8
        val FILTER_TWIRL = 9
        val FILTER_TUNNEL = 10
        val FILTER_BULGE = 11
        val FILTER_DENT = 12
        val FILTER_FISHEYE = 13
        val FILTER_STRETCH = 14
        val FILTER_MIRROR = 15

        private val SIZEOF_FLOAT = 4

        private val TEX_COORDS = floatArrayOf(0.0f, 0.0f, // 0 bottom left
                1.0f, 0.0f, // 1 bottom right
                0.0f, 1.0f, // 2 top left
                1.0f, 1.0f      // 3 top right
        )
        private val TEX_COORDS_BUF = GlUtil.createFloatBuffer(TEX_COORDS)
        private val TEX_COORDS_STRIDE = 2 * SIZEOF_FLOAT
    }

}