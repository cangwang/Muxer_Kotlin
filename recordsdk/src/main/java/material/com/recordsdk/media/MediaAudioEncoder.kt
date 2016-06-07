package material.com.recordsdk.media

/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaAudioEncoder.java
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

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log

import java.io.IOException
import java.nio.ByteBuffer

/**
 * 声音录制
 * 录制为音乐格式AAC
 */
class MediaAudioEncoder(muxer: MediaMuxerWrapper, listener: MediaEncoder.MediaEncoderListener) : MediaEncoder(muxer, listener) {
    //声音录制线程
    private var mAudioThread: AudioThread? = null

    //声音录制准备
    @Throws(IOException::class)
    override fun prepare() {
        if (DEBUG) Log.v(TAG, "prepare:")
        mTrackIndex = -1
        mMuxerStarted = false;
        mIsEOS = false
        // prepare MediaCodec for AAC encoding of audio data from inernal mic.
        //声音的信息
        val audioCodecInfo = selectAudioCodec(MIME_TYPE)
        if (audioCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE)
            return
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + audioCodecInfo.name)
        //声音录制信息初始化
        val audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1)
        //录制AAC格式
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        //录制声道
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
        //录制频率
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        //		audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
        //      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
        if (DEBUG) Log.i(TAG, "format: " + audioFormat)
        //创建录制格式
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        //编码器配置录制格式
        mMediaCodec!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec!!.start()
        if (DEBUG) Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener.onPrepared(this)
            } catch (e: Exception) {
                Log.e(TAG, "prepare:", e)
            }

        }
    }

    override fun startRecording() {
        super.startRecording()
        // create and execute audio capturing thread using internal mic
        if (mAudioThread == null) {
            mAudioThread = AudioThread()
            mAudioThread!!.start()
        }
    }

    override fun release() {
        mAudioThread = null
        super.release()
    }

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     */
    private inner class AudioThread : Thread() {
        override fun run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                //设置加载的字节长度
                val min_buffer_size = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT)
                var buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER
                if (buffer_size < min_buffer_size)
                    buffer_size = (min_buffer_size / SAMPLES_PER_FRAME + 1) * SAMPLES_PER_FRAME * 2

                var audioRecord: AudioRecord? = null
                for (source in AUDIO_SOURCES) {
                    try {
                        //获取可以获取的声音源
                        audioRecord = AudioRecord(
                                source, SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size)
                        if (audioRecord.state != AudioRecord.STATE_INITIALIZED)
                            audioRecord = null
                    } catch (e: Exception) {
                        audioRecord = null
                    }

                    if (audioRecord != null) break
                }
                if (audioRecord != null) {
                    try {
                        loop@ while (mIsCapturing) {
                            //线程堵塞
//                            synchronized (mSync) {
                                if (mIsCapturing && !mRequestStop && mRequestPause) {
                                    try {
                                        mSync.wait()
                                    } catch (e: InterruptedException) {
                                        break@loop
                                    }

                                    continue@loop
                                }
//                            }
                            if (mIsCapturing && !mRequestStop && !mRequestPause) {
                                if (DEBUG) Log.v(TAG, "AudioThread:start audio recording")
                                val buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME)
                                var readBytes: Int
                                //开始录制
                                audioRecord.startRecording()
                                try {
                                    while (mIsCapturing && !mRequestStop && !mRequestPause && !mIsEOS) {
                                        // read audio data from internal mic
                                        //获得声音数据
                                        buf.clear()
                                        readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME)
                                        if (readBytes > 0) {
                                            // set audio data to encoder
                                            buf.position(readBytes)
                                            buf.flip()
                                            //将字节编码
                                            encode(buf, readBytes, ptsUs)
                                            //释放字节
                                            frameAvailableSoon()
                                        }
                                    }
                                    frameAvailableSoon()
                                } finally {
                                    //停止录制
                                    audioRecord.stop()
                                }
                            }
                        }
                    } finally {
                        //释放声音录制
                        audioRecord.release()
                    }
                } else {
                    Log.e(TAG, "failed to initialize AudioRecord")
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioThread#run", e)
            }

            if (DEBUG) Log.v(TAG, "AudioThread:finished")
        }
    }

    companion object {
        private val DEBUG = false    // TODO set false on release
        private val TAG = "MediaAudioEncoder"

        //录制格式
        private val MIME_TYPE = "audio/mp4a-latm"
        //录制频率
        private val SAMPLE_RATE = 44100    // 44.1[KHz] is only setting guaranteed to be available on all devices.
        //录制bit值
        private val BIT_RATE = 64000
        val SAMPLES_PER_FRAME = 1024    // AAC, bytes/frame/channel
        val FRAMES_PER_BUFFER = 25    // AAC, frame/buffer/sec
        //声音源
        private val AUDIO_SOURCES = intArrayOf(MediaRecorder.AudioSource.MIC, //麦克风
                MediaRecorder.AudioSource.DEFAULT, //默认
                MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION)

        /**
         * 获取可以生成的MIME类型
         * select the first codec that match a specific MIME type
         * @param mimeType
         * *
         * @return
         */
        private fun selectAudioCodec(mimeType: String): MediaCodecInfo? {
            if (DEBUG) Log.v(TAG, "selectAudioCodec:")

            var result: MediaCodecInfo? = null
            // get the list of available codecs
            val numCodecs = MediaCodecList.getCodecCount()
            LOOP@ for (i in 0..numCodecs - 1) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    // skipp decoder
                    continue
                }
                //获取支持类型
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.name + ",MIME=" + types[j])
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        if (result == null) {
                            result = codecInfo
                            break@LOOP
                        }
                    }
                }
            }
            return result
        }
    }

}
