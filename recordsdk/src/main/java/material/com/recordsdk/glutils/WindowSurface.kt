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

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder

/**
 * Recordable EGL window surface.
 *
 *
 * It's good practice to explicitly release() the surface, preferably from a "finally" block.
 * This object owns the Surface; releasing this object will release the Surface as well.
 */
class WindowSurface : EglSurfaceBase {
    private var mSurface: Surface? = null

    /**
     * Associates an EGL surface with the native window surface.  The Surface will be
     * owned by WindowSurface, and released when release() is called.
     */
    constructor(eglCore: EglCore, surface: SurfaceHolder) : super(eglCore) {
        createWindowSurface(surface.surface)
        mSurface = surface.surface
    }

    /**
     * Associates an EGL surface with the native window surface.  The Surface will be
     * owned by WindowSurface, and released when release() is called.
     */
    constructor(eglCore: EglCore, surface: Surface) : super(eglCore) {
        createWindowSurface(surface)
        mSurface = surface
    }

    /**
     * Associates an EGL surface with the SurfaceTexture.
     */
    constructor(eglCore: EglCore, surfaceTexture: SurfaceTexture) : super(eglCore) {
        createWindowSurface(surfaceTexture)
    }

    /**
     * Releases any resources associated with the Surface and the EGL surface.
     */
    fun release() {
        releaseEglSurface()
        if (mSurface != null) {
            mSurface!!.release()
            mSurface = null
        }
    }

    /**
     * Recreate the EGLSurface, using the new EglCore.  The caller should have already
     * freed the old EGLSurface with releaseEglSurface().
     *
     *
     * This is useful when we want to update the EGLSurface associated with a Surface.
     * For example, if we want to share with a different EGLContext, which can only
     * be done by tearing down and recreating the context.  (That's handled by the caller;
     * this just creates a new EGLSurface for the Surface we were handed earlier.)
     *
     *
     * If the previous EGLSurface isn't fully destroyed, e.g. it's still current on a
     * context somewhere, the create call will fail with complaints from the Surface
     * about already being connected.
     */
    fun recreate(newEglCore: EglCore) {
        if (mSurface == null) {
            throw RuntimeException("not yet implemented for SurfaceTexture")
        }
        mEglCore = newEglCore          // switch to new context
        createWindowSurface(mSurface!!)  // create new surface
    }

}