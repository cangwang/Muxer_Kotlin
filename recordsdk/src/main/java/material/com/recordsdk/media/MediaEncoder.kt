package material.com.recordsdk.media

/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaEncoder.java
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

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log

import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

abstract class MediaEncoder(muxer: MediaMuxerWrapper?, protected val mListener: MediaEncoder.MediaEncoderListener?) : Runnable {

    interface MediaEncoderListener {
        fun onPrepared(encoder: MediaEncoder)
        fun onStopped(encoder: MediaEncoder)
    }

    protected val mSync = Object()
    /**
     * 屏幕捕抓状态
     * Flag that indicate this encoder is capturing now.
     */
    @Volatile protected var mIsCapturing: Boolean = false
    /**
     * Flag that indicate the frame data will be available soon.
     */
    private var mRequestDrain: Int = 0
    /**
     * Flag to request stop capturing
     */
    @Volatile protected var mRequestStop: Boolean = false
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected var mIsEOS: Boolean = false
    /**
     * Flag the indicate the muxer is running
     */
    protected var mMuxerStarted: Boolean = false
    /**
     * Track Number
     */
    protected var mTrackIndex: Int = 0
    /**
     * MediaCodec instance for encoding
     */
    protected var mMediaCodec: MediaCodec? = null                // API >= 16(Android4.1.2)
    /**
     * Weak refarence of MediaMuxerWarapper instance
     */
    protected val mWeakMuxer: WeakReference<MediaMuxerWrapper>?
    /**
     * BufferInfo instance for dequeuing
     */
    private var mBufferInfo: MediaCodec.BufferInfo? = null        // API >= 16(Android4.1.2)

    @Volatile protected var mRequestPause: Boolean = false
    private var mLastPausedTimeUs: Long = 0

    init {
        if (mListener == null) throw NullPointerException("MediaEncoderListener is null")
        if (muxer == null) throw NullPointerException("MediaMuxerWrapper is null")
        mWeakMuxer = WeakReference(muxer)
        muxer.addEncoder(this)
        synchronized (mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = MediaCodec.BufferInfo()
            // wait for starting thread
            Thread(this, javaClass.simpleName).start()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
            }

        }
    }

    val outputPath: String?
        get() {
            val muxer = mWeakMuxer!!.get()
            return muxer?.outputPath
        }

    /**
     * the method to indicate frame data is soon available or already available
     * @return return true if encoder is ready to encod.
     */
    fun frameAvailableSoon(): Boolean {
        //    	if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false
            }
            mRequestDrain++
            mSync.notifyAll()
        }
        return true
    }

    /**
     * encoding loop on private thread
     */
    override fun run() {
        //		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        synchronized (mSync) {
            mRequestStop = false
            mRequestDrain = 0
            mSync.notify()
        }
        val isRunning = true
        var localRequestStop: Boolean = false
        var localRequestDrain: Boolean = false
        while (isRunning) {
            synchronized (mSync) {
                localRequestStop = mRequestStop
                localRequestDrain = mRequestDrain > 0
                if (localRequestDrain)
                    mRequestDrain--
            }
            if (localRequestStop) {
                drain()
                // request stop recording
                signalEndOfInputStream()
                // process output data again for EOS signale
                drain()
                // release all related objects
                release()
                break
            }
            if (localRequestDrain) {
                drain()
            } else {
//                synchronized (mSync) {
//                    try {
//                        mSync.wait()
//                    } catch (e: InterruptedException) {
//                        break
//                    }
//
//                }
            }
        } // end of while
        if (DEBUG) Log.d(TAG, "Encoder thread exiting")
        synchronized (mSync) {
            mRequestStop = true
            mIsCapturing = false
        }
    }

    /*
    * prepareing method for each sub class
    * this method should be implemented in sub class, so set this as abstract method
    * @throws IOException
    */
    /*package*/ @Throws(IOException::class)
    internal abstract fun prepare()

    /*package*/ internal open fun startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording")
        synchronized (mSync) {
            mIsCapturing = true
            mRequestStop = false
            mRequestPause = false
            mSync.notifyAll()
        }
    }

    /**
     * the method to request stop encoding
     */
    /*package*/ internal open fun stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording")
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return
            }
            mRequestStop = true    // for rejecting newer frame
            mSync.notifyAll()
            // We can not know when the encoding and writing finish.
            // so we return immediately after request to avoid delay of caller thread
        }
    }

    /*package*/ internal fun pauseRecording() {
        if (DEBUG) Log.v(TAG, "pauseRecording")
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return
            }
            mRequestPause = true
            mLastPausedTimeUs = System.nanoTime() / 1000
            mSync.notifyAll()
        }
    }

    /*package*/ internal fun resumeRecording() {
        if (DEBUG) Log.v(TAG, "resumeRecording")
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return
            }
            offsetPTSUs = System.nanoTime() / 1000 - mLastPausedTimeUs
            mRequestPause = false
            mSync.notifyAll()
        }
    }

    //********************************************************************************
    //********************************************************************************
    /**
     * Release all releated objects
     */
    protected open fun release() {
        if (DEBUG) Log.d(TAG, "release:")
        try {
            mListener!!.onStopped(this)
        } catch (e: Exception) {
            Log.e(TAG, "failed onStopped", e)
        }

        mIsCapturing = false
        if (mMediaCodec != null) {
            try {
                mMediaCodec!!.stop()
                mMediaCodec!!.release()
                mMediaCodec = null
            } catch (e: Exception) {
                Log.e(TAG, "failed releasing MediaCodec", e)
            }

        }
        if (mMuxerStarted) {
            val muxer = mWeakMuxer?.get()
            if (muxer != null) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "failed stopping muxer", e)
                }

            }
        }
        mBufferInfo = null
    }

    protected open fun signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder")
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
        //		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode(null, 0, ptsUs)
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     * @param buffer
     * *
     * @param length　length of byte array, zero means EOS.
     * *
     * @param presentationTimeUs
     */
    protected fun encode(buffer: ByteBuffer?, length: Int, presentationTimeUs: Long) {
        if (!mIsCapturing) return
        val inputBuffers = mMediaCodec!!.inputBuffers
        while (mIsCapturing) {
            val inputBufferIndex = mMediaCodec!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                if (buffer != null) {
                    inputBuffer.put(buffer)
                }
                //	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
                if (length <= 0) {
                    // send EOS
                    mIsEOS = true
                    if (DEBUG) Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM")
                    mMediaCodec!!.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                } else {
                    mMediaCodec!!.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0)
                }
                break
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * drain encoded data and write them to muxer
     */
    protected fun drain() {
        if (mMediaCodec == null) return
        var encoderOutputBuffers = mMediaCodec!!.outputBuffers
        var encoderStatus: Int
        var count = 0
        val muxer = mWeakMuxer!!.get()
        if (muxer == null) {
            //        	throw new NullPointerException("muxer is unexpectedly null");
            Log.w(TAG, "muxer is unexpectedly null")
            return
        }
        LOOP@ while (mIsCapturing) {
            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus = mMediaCodec!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                if (!mIsEOS) {
                    if (++count > 5)
                        break@LOOP        // out of while
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED")
                // this shoud not come when encoding
                encoderOutputBuffers = mMediaCodec!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED")
                // this status indicate the output format of codec is changed
                // this should come only once before actual encoded data
                // but this status never come on Android4.3 or less
                // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                if (mMuxerStarted) {
                    // second time request is error
                    throw RuntimeException("format changed twice")
                }
                // get output format from codec and pass them to muxer
                // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                val format = mMediaCodec!!.outputFormat // API >= 16
                mTrackIndex = muxer.addTrack(format)
                mMuxerStarted = true
                if (!muxer.start()) {
                    // we should wait until muxer is ready
//                    synchronized (muxer) {
                        while (!muxer.isStarted)
                            try {
                                muxer.wait(100)
                            } catch (e: InterruptedException) {
                                break@LOOP
                            }

//                    }
                }
            } else if (encoderStatus < 0) {
                // unexpected status
                if (DEBUG) Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus)
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus] ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null") // this never should come...may be a MediaCodec internal error
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // You shoud set output format to muxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and prepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo!!.size = 0
                }

                if (mBufferInfo!!.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will prrograming failure.
                        throw RuntimeException("drain:muxer hasn't started")
                    }
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    //					if (!mRequestPause) {
                    mBufferInfo!!.presentationTimeUs = ptsUs
                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo!!)
                    prevOutputPTSUs = mBufferInfo!!.presentationTimeUs
                    //					}
                }
                // return buffer to encoder
                mMediaCodec!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    // when EOS come.
                    mIsCapturing = false
                    break      // out of while
                }
            }
        }
    }

    /**
     * previous presentationTimeUs for writing
     */
    private var prevOutputPTSUs: Long = 0

    private var offsetPTSUs: Long = 0
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected // presentationTimeUs should be monotonic
            // otherwise muxer fail to write
    val ptsUs: Long
        get() {
            var result: Long = 0
            synchronized (mSync) {
                result = System.nanoTime() / 1000L - offsetPTSUs
            }
            if (result < prevOutputPTSUs)
                result = prevOutputPTSUs - result + result
            return result
        }

    companion object {
        private val DEBUG = false    // TODO set false on release
        private val TAG = "MediaEncoder"

        protected val TIMEOUT_USEC = 10000    // 10[msec]
        protected val MSG_FRAME_AVAILABLE = 1
        protected val MSG_STOP_RECORDING = 9
    }

}
