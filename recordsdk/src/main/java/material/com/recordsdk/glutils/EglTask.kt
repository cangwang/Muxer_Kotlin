package material.com.recordsdk.glutils

/*
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: EglTask.java
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

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue

import android.opengl.EGLContext
import android.util.Log

abstract class EglTask(shared_context: EGLContext?, flags: Int) : Runnable {

    protected class Request(internal var request: Int, internal var arg1: Int, internal var arg2: Any?) {

        override fun equals(o: Any?): Boolean {
            return if (o is Request)
                request == o.request && arg1 == o.arg1 && arg2 === o.arg2
            else
                super.equals(o)
        }
    }

    private val mSync = Object()
    private val mRequestPool = LinkedBlockingQueue<Request>()
    private val mRequestQueue = LinkedBlockingDeque<Request>()
    private var mIsRunning = true
    var eglCore: EglCore? = null
        private set
    private var mEglHolder: OffScreenSurface? = null

    init {
        Log.i(TAG, "shared_context=" + shared_context)
        offer(REQUEST_EGL_TASK_START, flags, shared_context!!)
    }

    protected abstract fun onStart()
    protected abstract fun onStop()
    protected abstract fun onError(e: Exception): Boolean
    protected abstract fun processRequest(request: Int, arg1: Int, arg2: Any): Boolean

    override fun run() {
        var request: Request? = null
        try {
            request = mRequestQueue.take()
        } catch (e: InterruptedException) {
            // ignore
        }

        synchronized (mSync) {
            if (request!!.arg2 == null || request!!.arg2 is EGLContext)
                eglCore = EglCore(request!!.arg2 as EGLContext?, request!!.arg1)
            mSync.notifyAll()
            if (eglCore == null) {
                callOnError(RuntimeException("failed to create EglCore"))
                return
            }
        }
        mEglHolder = OffScreenSurface(eglCore!!, 1, 1)
        mEglHolder!!.makeCurrent()
        try {
            onStart()
        } catch (e: Exception) {
            if (callOnError(e))
                mIsRunning = false
        }

        LOOP@ while (mIsRunning) {
            try {
                request = mRequestQueue.take()
                mEglHolder!!.makeCurrent()
                when (request!!.request) {
                    REQUEST_EGL_TASK_NON -> {
                    }
                    REQUEST_EGL_TASK_RUN -> if (request.arg2 is Runnable)
                        try {
                            (request.arg2 as Runnable).run()
                        } catch (e: Exception) {
                            if (callOnError(e))
                                break@LOOP
                        }

                    REQUEST_EGL_TASK_QUIT -> break@LOOP
                    else -> {
                        var result = false
                        try {
                            result = processRequest(request.request, request.arg1, request.arg2!!)
                        } catch (e: Exception) {
                            if (callOnError(e))
                                break@LOOP
                        }

                        if (result)
                            break@LOOP
                    }
                }
                request.request = REQUEST_EGL_TASK_NON
                mRequestPool.offer(request)
            } catch (e: InterruptedException) {
                break
            }

        }
        mEglHolder!!.makeCurrent()
        try {
            onStop()
        } catch (e: Exception) {
            callOnError(e)
        }

        mEglHolder!!.release()
        eglCore!!.release()
        synchronized (mSync) {
            mIsRunning = false
            mSync.notifyAll()
        }
    }

    private fun callOnError(e: Exception): Boolean {
        try {
            return onError(e)
        } catch (e2: Exception) {
            Log.e(TAG, "exception occurred in callOnError", e)
        }

        return true
    }

    protected fun obtain(request: Int, arg1: Int, arg2: Any): Request {
        var req: Request? = mRequestPool.poll()
        if (req != null) {
            req.request = request
            req.arg1 = arg1
            req.arg2 = arg2
        } else {
            req = Request(request, arg1, arg2)
        }
        return req
    }

    /**
     * offer request to run on worker thread
     * @param request minus values and zero are reserved
     * *
     * @param arg1
     * *
     * @param arg2
     */
    fun offer(request: Int, arg1: Int, arg2: Any) {
        mRequestQueue.offer(obtain(request, arg1, arg2))
    }

    /**
     * offer request to run on worker thread on top of the request queue
     * @param request minus values and zero are reserved
     * *
     * @param arg1
     * *
     * @param arg2
     */
    fun offerFirst(request: Int, arg1: Int, arg2: Any?) {
        mRequestQueue.offerFirst(obtain(request, arg1, arg2!!))
    }

    /**
     * request to run on worker thread
     * @param task
     */
    fun queueEvent(task: Runnable?) {
        if (task != null)
            mRequestQueue.offer(obtain(REQUEST_EGL_TASK_RUN, 0, task))
    }

//    fun removeRequest(request: Request) {
//        while (mRequestQueue.remove(request)) {
//        }
//    }

    protected fun makeCurrent() {
        mEglHolder!!.makeCurrent()
    }

    /**
     * request terminate worker thread and release all related resources
     */
    fun release() {
        mRequestQueue.clear()
        synchronized (mSync) {
            if (mIsRunning) {
                offerFirst(REQUEST_EGL_TASK_QUIT, 0, null)
                mIsRunning = false
                try {
                    mSync.wait()
                } catch (e: InterruptedException) {
                    // ignore
                }

            }
        }
    }

    fun releaseSelf() {
        mRequestQueue.clear()
        synchronized (mSync) {
            if (mIsRunning) {
                offerFirst(REQUEST_EGL_TASK_QUIT, 0, null)
                mIsRunning = false
            }
        }
    }

    companion object {
        private val TAG = "EglTask"

        // minus value is reserved for internal use
        private val REQUEST_EGL_TASK_NON = 0
        private val REQUEST_EGL_TASK_RUN = -1
        private val REQUEST_EGL_TASK_START = -8
        private val REQUEST_EGL_TASK_QUIT = -9
    }

}
