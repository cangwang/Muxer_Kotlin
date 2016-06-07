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

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.MotionEvent

import java.nio.FloatBuffer

/**
 * GL program and supporting functions for textured 2D shapes.
 */
class Texture2dProgram
/**
 * Prepares the program in the current EGL context.
 */
(
        /**
         * Returns the program type.
         */
        val programType: Texture2dProgram.ProgramType) {

    enum class ProgramType {
        TEXTURE_2D, TEXTURE_FILT3x3,
        TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_NIGHT, TEXTURE_EXT_CHROMA_KEY,
        TEXTURE_EXT_SQUEEZE, TEXTURE_EXT_TWIRL, TEXTURE_EXT_TUNNEL, TEXTURE_EXT_BULGE,
        TEXTURE_EXT_DENT, TEXTURE_EXT_FISHEYE, TEXTURE_EXT_STRETCH, TEXTURE_EXT_MIRROR,
        TEXTURE_EXT_FILT3x3
    }

    private var mTexWidth: Float = 0.toFloat()
    private var mTexHeight: Float = 0.toFloat()

    // Handles to the GL program and various components of it.
    private var mProgramHandle: Int = 0
    private val muMVPMatrixLoc: Int
    private val muTexMatrixLoc: Int
    private var muKernelLoc: Int = 0
    private var muTexOffsetLoc: Int = 0
    private var muColorAdjustLoc: Int = 0
    private val maPositionLoc: Int
    private val maTextureCoordLoc: Int
    private var muTouchPositionLoc: Int = 0

    private var mTextureTarget: Int = 0

    private val mKernel = FloatArray(KERNEL_SIZE)       // Inputs for convolution filter based shaders
    private val mSummedTouchPosition = FloatArray(2)    // Summed touch event delta
    private val mLastTouchPosition = FloatArray(2)      // Raw location of last touch event
    private var mTexOffset: FloatArray? = null
    private var mColorAdjust: Float = 0.toFloat()


    init {

        when (programType) {
            Texture2dProgram.ProgramType.TEXTURE_2D -> {
                mTextureTarget = GLES20.GL_TEXTURE_2D
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)
            }
            Texture2dProgram.ProgramType.TEXTURE_FILT3x3 -> {
                mTextureTarget = GLES20.GL_TEXTURE_2D
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FILT3x3)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_BW -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_NIGHT -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_NIGHT)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_CHROMA_KEY -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_CHROMA_KEY)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_SQUEEZE -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_SQUEEZE)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_TWIRL -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_TWIRL)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_TUNNEL -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_TUNNEL)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_BULGE -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_BULGE)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_FISHEYE -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FISHEYE)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_DENT -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_DENT)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_MIRROR -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_MIRROR)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_STRETCH -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_STRETCH)
            }
            Texture2dProgram.ProgramType.TEXTURE_EXT_FILT3x3 -> {
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT3x3)
            }
            else -> throw RuntimeException("Unhandled type " + programType)
        }
        if (mProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }
        if (DEBUG) Log.d(TAG, "Created program $mProgramHandle ($programType)")

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition")
        GlUtil.checkLocation(maPositionLoc, "aPosition")
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord")
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord")
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix")
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix")
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix")
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix")
        muKernelLoc = GLES20.glGetUniformLocation(mProgramHandle, "uKernel")
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1
            muTexOffsetLoc = -1
            muColorAdjustLoc = -1
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexOffset")
            GlUtil.checkLocation(muTexOffsetLoc, "uTexOffset")
            muColorAdjustLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorAdjust")
            GlUtil.checkLocation(muColorAdjustLoc, "uColorAdjust")

            // initialize default values
            setKernel(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f), 0f)
            setTexSize(256, 256)
        }

        muTouchPositionLoc = GLES20.glGetUniformLocation(mProgramHandle, "uPosition")
        if (muTouchPositionLoc < 0) {
            // Shader doesn't use position
            muTouchPositionLoc = -1
        } else {
            // initialize default values
            //handleTouchEvent(new float[]{0f, 0f});
        }
    }

    /**
     * Releases the program.
     */
    fun release() {
        if (DEBUG) Log.d(TAG, "deleting program " + mProgramHandle)
        GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
    }

    /**
     * Creates a texture object suitable for use with this program.
     *
     *
     * On exit, the texture will be bound.
     */
    fun createTextureObject(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GlUtil.checkGlError("glGenTextures")

        val texId = textures[0]
        GLES20.glBindTexture(mTextureTarget, texId)
        GlUtil.checkGlError("glBindTexture " + texId)

        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE)
        GlUtil.checkGlError("glTexParameter")

        return texId
    }

    /**
     * Configures the effect offset

     * This only has an effect for programs that
     * use positional effects like SQUEEZE and MIRROR
     */
    fun handleTouchEvent(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_MOVE) {
            // A finger is dragging about
            if (mTexHeight != 0f && mTexWidth != 0f) {
                mSummedTouchPosition[0] += 2 * (ev.x - mLastTouchPosition[0]) / mTexWidth
                mSummedTouchPosition[1] += 2 * (ev.y - mLastTouchPosition[1]) / -mTexHeight
                mLastTouchPosition[0] = ev.x
                mLastTouchPosition[1] = ev.y
            }
        } else if (ev.action == MotionEvent.ACTION_DOWN) {
            // The primary finger has landed
            mLastTouchPosition[0] = ev.x
            mLastTouchPosition[1] = ev.y
        }
    }

    /**
     * Configures the convolution filter values.
     * This only has an effect for programs that use the
     * FRAGMENT_SHADER_EXT_FILT3x3 Fragment shader.

     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    fun setKernel(values: FloatArray, colorAdj: Float) {
        if (values.size != KERNEL_SIZE) {
            throw IllegalArgumentException("Kernel size is " + values.size +
                    " vs. " + KERNEL_SIZE)
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE)
        mColorAdjust = colorAdj
        //Log.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    fun setTexSize(width: Int, height: Int) {
        mTexHeight = height.toFloat()
        mTexWidth = width.toFloat()
        val rw = 1.0f / width
        val rh = 1.0f / height

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = floatArrayOf(-rw, -rh, 0f, -rh, rw, -rh, -rw, 0f, 0f, 0f, rw, 0f, -rw, rh, 0f, rh, rw, rh)
        //Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    /**
     * Issues the draw call.  Does the full setup on every call.

     * @param mvpMatrix The 4x4 projection matrix.
     * *
     * @param vertexBuffer Buffer with vertex position data.
     * *
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * *
     * @param vertexCount Number of vertices in vertexBuffer.
     * *
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * *
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     * *        vertexCount * sizeof(float)).
     * *
     * @param texMatrix A 4x4 transformation matrix for texture coords.
     * *
     * @param texBuffer Buffer with vertex texture data.
     * *
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    fun draw(mvpMatrix: FloatArray, vertexBuffer: FloatBuffer, firstVertex: Int,
             vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
             texMatrix: FloatArray, texBuffer: FloatBuffer, textureId: Int, texStride: Int) {
        GlUtil.checkGlError("draw start")

        // Select the program.
        GLES20.glUseProgram(mProgramHandle)
        GlUtil.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(mTextureTarget, textureId)
        GlUtil.checkGlError("glBindTexture")

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        GlUtil.checkGlError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer)
        GlUtil.checkGlError("glVertexAttribPointer")

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0)
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0)
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust)
        }

        // Populate touch position data, if present
        if (muTouchPositionLoc >= 0) {
            GLES20.glUniform2fv(muTouchPositionLoc, 1, mSummedTouchPosition, 0)
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc)
        GLES20.glBindTexture(mTextureTarget, 0)
        GLES20.glUseProgram(0)
    }

    companion object {
        private val DEBUG = false
        private val TAG = "Texture2dProgram"

        // Simple vertex shader, used for all programs.
        private val VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uTexMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec4 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                        "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                        "}\n"

        // Simple fragment shader for use with "normal" 2D textures.
        private val FRAGMENT_SHADER_2D =
                "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform sampler2D sTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n"

        // Simple fragment shader for use with external 2D textures (e.g. what we get from
        // SurfaceTexture).
        private val FRAGMENT_SHADER_EXT =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n"

        // Fragment shader that converts color to black & white with a simple transformation.
        private val FRAGMENT_SHADER_EXT_BW =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
                        "    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
                        "    gl_FragColor = vec4(color, color, color, 1.0);\n" +
                        "}\n"

        // Fragment shader that attempts to produce a high contrast image
        private val FRAGMENT_SHADER_EXT_NIGHT =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
                        "    float color = ((tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11) - 0.5 * 1.5) + 0.8;\n" +
                        "    gl_FragColor = vec4(color, color + 0.15, color, 1.0);\n" +
                        "}\n"

        // Fragment shader that applies a Chroma Key effect, making green pixels transparent
        private val FRAGMENT_SHADER_EXT_CHROMA_KEY =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "void main() {\n" +
                        "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
                        "    float color = ((tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11) - 0.5 * 1.5) + 0.8;\n" +
                        "    if(tc.g > 0.6 && tc.b < 0.6 && tc.r < 0.6){ \n" +
                        "        gl_FragColor = vec4(0, 0, 0, 0.0);\n" +
                        "    }else{ \n" +
                        "        gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "    }\n" +
                        "}\n"

        private val FRAGMENT_SHADER_SQUEEZE =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    r = pow(r, 1.0/1.8) * 0.8;\n" + // Squeeze it

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_TWIRL =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    phi = phi + (1.0 - smoothstep(-0.5, 0.5, r)) * 4.0;\n" + // Twirl it

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_TUNNEL =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    if (r > 0.5) r = 0.5;\n" + // Tunnel

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_BULGE =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    r = r * smoothstep(-0.1, 0.5, r);\n" + // Bulge

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_DENT =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    r = 2.0 * r - r * smoothstep(0.0, 0.7, r);\n" + // Dent

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_FISHEYE =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    float r = length(normCoord); // to polar coords \n" +
                        "    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n" +
                        "    r = r * r / sqrt(2.0);\n" + // Fisheye

                        "    normCoord.x = r * cos(phi); \n" +
                        "    normCoord.y = r * sin(phi); \n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_STRETCH =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    vec2 s = sign(normCoord + uPosition);\n" +
                        "    normCoord = abs(normCoord);\n" +
                        "    normCoord = 0.5 * normCoord + 0.5 * smoothstep(0.25, 0.5, normCoord) * normCoord;\n" +
                        "    normCoord = s * normCoord;\n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        private val FRAGMENT_SHADER_MIRROR =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "uniform vec2 uPosition;\n" +
                        "void main() {\n" +
                        "    vec2 texCoord = vTextureCoord.xy;\n" +
                        "    vec2 normCoord = 2.0 * texCoord - 1.0;\n" +
                        "    normCoord.x = normCoord.x * sign(normCoord.x + uPosition.x);\n" +
                        "    texCoord = normCoord / 2.0 + 0.5;\n" +
                        "    gl_FragColor = texture2D(sTexture, texCoord);\n" +
                        "}\n"

        // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
        // the lower-right half will have the filter applied, and a thin red line will be drawn
        // at the border.
        //
        // This is not optimized for performance.  Some things that might make this faster:
        // - Remove the conditionals.  They're used to present a half & half view with a red
        //   stripe across the middle, but that's only useful for a demo.
        // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
        // - Bake the filter kernel into the shader, instead of passing it through a uniform
        //   array.  That, combined with loop unrolling, should reduce memory accesses.
        val KERNEL_SIZE = 9
        private val FRAGMENT_SHADER_EXT_FILT3x3 =
                "#extension GL_OES_EGL_image_external : require\n#define KERNEL_SIZE $KERNEL_SIZE\nprecision highp float;\nvarying vec2 vTextureCoord;\nuniform samplerExternalOES sTexture;\nuniform float uKernel[KERNEL_SIZE];\nuniform vec2 uTexOffset[KERNEL_SIZE];\nuniform float uColorAdjust;\nvoid main() {\n    int i = 0;\n    vec4 sum = vec4(0.0);\n    for (i = 0; i < KERNEL_SIZE; i++) {\n            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n            sum += texc * uKernel[i];\n    }\n    sum += uColorAdjust;\n    gl_FragColor = sum;\n}\n"

        private val FRAGMENT_SHADER_FILT3x3 =
                "#define KERNEL_SIZE $KERNEL_SIZE\nprecision highp float;\nvarying vec2 vTextureCoord;\nuniform sampler2D sTexture;\nuniform float uKernel[KERNEL_SIZE];\nuniform vec2 uTexOffset[KERNEL_SIZE];\nuniform float uColorAdjust;\nvoid main() {\n    int i = 0;\n    vec4 sum = vec4(0.0);\n    for (i = 0; i < KERNEL_SIZE; i++) {\n            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n            sum += texc * uKernel[i];\n    }\n    sum += uColorAdjust;\n    gl_FragColor = sum;\n}\n"
    }
}