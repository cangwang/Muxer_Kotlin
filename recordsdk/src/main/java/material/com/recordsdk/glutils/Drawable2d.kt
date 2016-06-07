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

import java.nio.FloatBuffer

/**
 * Base class for stuff we like to draw.
 */
class Drawable2d
/**
 * Prepares a drawable from a "pre-fabricated" shape definition.
 *
 *
 * Does no EGL/GL operations, so this can be done at any time.
 */
(private val mPrefab: Drawable2d.Prefab?) {

    /**
     * Returns the array of vertices.
     *
     *
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    var vertexArray: FloatBuffer? = null
        private set
    /**
     * Returns the number of vertices stored in the vertex array.
     */
    var vertexCount: Int = 0
        private set
    /**
     * Returns the number of position coordinates per vertex.  This will be 2 or 3.
     */
    var coordsPerVertex: Int = 0
        private set
    /**
     * Returns the width, in bytes, of the data for each vertex.
     */
    var vertexStride: Int = 0
        private set

    /**
     * Enum values for constructor.
     */
    enum class Prefab {
        TRIANGLE, RECTANGLE, FULL_RECTANGLE
    }

    init {
        when (mPrefab) {
            Drawable2d.Prefab.TRIANGLE -> {
                vertexArray = TRIANGLE_BUF
                coordsPerVertex = 2
                vertexStride = coordsPerVertex * SIZEOF_FLOAT
                vertexCount = TRIANGLE_COORDS.size / coordsPerVertex
            }
            Drawable2d.Prefab.RECTANGLE -> {
                vertexArray = RECTANGLE_BUF
                coordsPerVertex = 2
                vertexStride = coordsPerVertex * SIZEOF_FLOAT
                vertexCount = RECTANGLE_COORDS.size / coordsPerVertex
            }
            Drawable2d.Prefab.FULL_RECTANGLE -> {
                vertexArray = FULL_RECTANGLE_BUF
                coordsPerVertex = 2
                vertexStride = coordsPerVertex * SIZEOF_FLOAT
                vertexCount = FULL_RECTANGLE_COORDS.size / coordsPerVertex
            }
            else -> throw RuntimeException("Unknown shape " + mPrefab)
        }
    }

    override fun toString(): String {
        if (mPrefab != null) {
            return "[Drawable2d: $mPrefab]"
        } else {
            return "[Drawable2d: ...]"
        }
    }

    companion object {
        /**
         * Simple triangle (roughly equilateral, 1.0 per side).
         */
        private val TRIANGLE_COORDS = floatArrayOf(0.0f, 0.622008459f, // top
                -0.5f, -0.311004243f, // bottom left
                0.5f, -0.311004243f    // bottom right
        )
        private val TRIANGLE_BUF = GlUtil.createFloatBuffer(TRIANGLE_COORDS)

        /**
         * Simple square, specified as a triangle strip.  The square is centered on (0,0) and has
         * a size of 1x1.
         *
         *
         * Triangles are 0-1-2 and 2-1-3 (counter-clockwise winding).
         */
        private val RECTANGLE_COORDS = floatArrayOf(-0.5f, -0.5f, // 0 bottom left
                0.5f, -0.5f, // 1 bottom right
                -0.5f, 0.5f, // 2 top left
                0.5f, 0.5f)// 3 top right
        private val RECTANGLE_BUF = GlUtil.createFloatBuffer(RECTANGLE_COORDS)

        /**
         * A "full" square, extending from -1 to +1 in both dimensions.  When the model/view/projection
         * matrix is identity, this will exactly cover the viewport.
         *
         *
         * This has texture coordinates as well.
         */
        private val FULL_RECTANGLE_COORDS = floatArrayOf(-1.0f, -1.0f, // 0 bottom left
                1.0f, -1.0f, // 1 bottom right
                -1.0f, 1.0f, // 2 top left
                1.0f, 1.0f)// 3 top right
        private val FULL_RECTANGLE_BUF = GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS)

        private val SIZEOF_FLOAT = 4
    }
}