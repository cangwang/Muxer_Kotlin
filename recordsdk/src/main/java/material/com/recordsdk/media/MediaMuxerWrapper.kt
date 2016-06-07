package material.com.recordsdk.media

/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2014-15 saki t_saki@serenegiant.com
 *
 * File name: MediaMuxerWrapper.java
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
import android.media.MediaMuxer
import android.os.Environment
import android.text.TextUtils
import android.util.Log

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class MediaMuxerWrapper:Object {
    //输入路径
    /**
     * 获取输出地址
     * @return
     */
    var outputPath: String? = null
        private set
    //编码混合
    private val mMediaMuxer: MediaMuxer// API >= 18
    //编码个数
    private var mEncoderCount: Int = 0
    private var mStatredCount: Int = 0
    //录制开始标志
    /**
     * 返回是否在录制
     * @return
     */
    var isStarted: Boolean = false
        private set
    //录制状态
    /**
     * 返回是否暂停
     * @return
     */
    @Volatile var isPaused: Boolean = false
        private set
    private var mVideoEncoder: MediaEncoder? = null
    private var mAudioEncoder: MediaEncoder? = null

    /**
     * Constructor
     * @param ext extension of output file
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    constructor(ext: String) {
        var ext = ext
        if (TextUtils.isEmpty(ext)) ext = ".mp4"
        try {
            //初始化输出地址
            outputPath = getCaptureFile(Environment.DIRECTORY_MOVIES, ext)!!.toString()
        } catch (e: NullPointerException) {
            throw RuntimeException("This app has no permission of writing external storage")
        }

        //初始化编码
        mMediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mEncoderCount = 0
        mStatredCount = 0
        isStarted = false
    }

    /**
     * 带地址的输出方法
     * @param directory 目录
     * *
     * @param filename 文件名
     * *
     * @param ext 格式
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    constructor(directory: String, filename: String, ext: String) {
        var ext = ext
        if (TextUtils.isEmpty(ext)) ext = ".mp4"
        try {
            outputPath = setFile(directory, filename, ext)!!.toString()
        } catch (e: NullPointerException) {
            throw RuntimeException("This app has no permission of writing external storage")
        }

        mMediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mEncoderCount = 0
        mStatredCount = 0
        isStarted = false
    }

    /**
     * 编码准备
     * @throws IOException
     */
    @Throws(IOException::class)
    fun prepare() {
        if (mVideoEncoder != null)
        //初始化屏幕录制
            mVideoEncoder!!.prepare()
        if (mAudioEncoder != null)
        //初始化声音录制
            mAudioEncoder!!.prepare()
    }

    /**
     * 开始录制
     */
    fun startRecording() {
        if (mVideoEncoder != null)
        //屏幕录制开始
            mVideoEncoder!!.startRecording()
        if (mAudioEncoder != null)
        //声音录制开始
            mAudioEncoder!!.startRecording()
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder!!.stopRecording()
        mVideoEncoder = null
        if (mAudioEncoder != null)
            mAudioEncoder!!.stopRecording()
        mAudioEncoder = null
    }


    /**
     * 暂停录制
     */
    @Synchronized fun pauseRecording() {
        isPaused = true
        if (mVideoEncoder != null)
            mVideoEncoder!!.pauseRecording()
        if (mAudioEncoder != null)
            mAudioEncoder!!.pauseRecording()
    }

    /**
     * 恢复录制
     */
    @Synchronized fun resumeRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder!!.resumeRecording()
        if (mAudioEncoder != null)
            mAudioEncoder!!.resumeRecording()
        isPaused = false
    }


    //**********************************************************************
    //**********************************************************************
    /**
     * 绑定视频和音频编码器是否加入到混合器中
     * assign encoder to this class. this is called from encoder.
     * @param encoder instance of MediaVideoEncoderBase
     */
    /*package*/ internal fun addEncoder(encoder: MediaEncoder) {
        if (encoder is MediaVideoEncoderBase) {
            if (mVideoEncoder != null)
                throw IllegalArgumentException("Video encoder already added.")
            mVideoEncoder = encoder
        } else if (encoder is MediaAudioEncoder) {
            if (mAudioEncoder != null)
                throw IllegalArgumentException("Video encoder already added.")
            mAudioEncoder = encoder
        } else
            throw IllegalArgumentException("unsupported encoder")
        //编码器个数
        mEncoderCount = if (mVideoEncoder != null) 1 else 0 + if (mAudioEncoder != null) 1 else 0
    }

    /**
     * 编码器启动
     * request start recording from encoder
     * @return true when muxer is ready to write
     */
    /*package*/ @Synchronized internal fun start(): Boolean {
        if (DEBUG) Log.v(TAG, "start:")
        mStatredCount++
        if (mEncoderCount > 0 && mStatredCount == mEncoderCount) {
            mMediaMuxer.start()
            isStarted = true
            notifyAll()
            if (DEBUG) Log.v(TAG, "MediaMuxer started:")
        }
        return isStarted
    }

    /**
     * request stop recording from encoder when encoder received EOS
     */
    /*package*/ @Synchronized internal fun stop() {
        if (DEBUG) Log.v(TAG, "stop:mStatredCount=" + mStatredCount)
        mStatredCount--
        if (mEncoderCount > 0 && mStatredCount <= 0) {
            mMediaMuxer.stop()
            mMediaMuxer.release()
            isStarted = false
            if (DEBUG) Log.v(TAG, "MediaMuxer stopped:")
        }
    }

    /**
     * assign encoder to muxer
     * @param format
     * *
     * @return minus value indicate error
     */
    /*package*/ @Synchronized internal fun addTrack(format: MediaFormat): Int {
        if (isStarted)
            throw IllegalStateException("muxer already started")
        val trackIx = mMediaMuxer.addTrack(format)
        if (DEBUG) Log.i(TAG, "addTrack:trackNum=$mEncoderCount,trackIx=$trackIx,format=$format")
        return trackIx
    }

    /**
     * write encoded data to muxer
     * @param trackIndex
     * *
     * @param byteBuf
     * *
     * @param bufferInfo
     */
    /*package*/ @Synchronized internal fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (mStatredCount > 0) {
            mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo)
        }
    }

    companion object {
        private val DEBUG = false    // TODO set false on release
        private val TAG = "MediaMuxerWrapper"
        //录制文件保存地址
        private val DIR_NAME = "YYRecord"
        //日期格式
        private val mDateTimeFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

        //**********************************************************************
        //**********************************************************************
        /**
         * 组合默认的输出文件地址
         * generate output file
         * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM etc.
         * *
         * @param ext .mp4(.m4a for audio) or .png
         * *
         * @return return null when this app has no writing permission to external storage.
         */
        fun getCaptureFile(type: String, ext: String): File? {
            val dir = File(Environment.getExternalStoragePublicDirectory(type), DIR_NAME)
            Log.d(TAG, "path=" + dir.toString())
            dir.mkdirs()
            if (dir.canWrite()) {
                return File(dir, dateTimeString + ext)
            }
            return null
        }

        /**
         * 组合自定义输出文件地址
         * @param directory
         * *
         * @param filename
         * *
         * @param ext
         * *
         * @return
         */
        fun setFile(directory: String?, filename: String?, ext: String): File? {
            val dir: File
            if (directory != null) {
                dir = File(Environment.getExternalStorageDirectory(), directory)
            } else {
                dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), directory)
            }

            dir.mkdirs()
            if (dir.canWrite()) {
                if (filename != null) {
                    return File(dir, filename + ext)
                } else {
                    return File(dir, dateTimeString + ext)
                }
            }
            return null
        }

        /**
         * 获取当前日期时间
         * get current date and time as String
         * @return
         */
        private val dateTimeString: String
            get() {
                val now = GregorianCalendar()
                return mDateTimeFormat.format(now.time)
            }
    }

}
