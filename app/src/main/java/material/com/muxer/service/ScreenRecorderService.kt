package material.com.muxer.service

/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: ScreenRecorderService.java
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

import android.app.IntentService
import android.content.Intent
import android.util.Log

import material.com.recordsdk.media.MediaRecord

class ScreenRecorderService : IntentService(ScreenRecorderService.TAG) {
    private var mMediaRecord: MediaRecord? = null

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.v(TAG, "onCreate:")
        //获取录制单例
        mMediaRecord = MediaRecord.getInstance(this)
    }

    override fun onHandleIntent(intent: Intent) {
        if (DEBUG) Log.v(TAG, "onHandleIntent:intent=" + intent)
        val action = intent.action
        if (ACTION_START == action) {
            //启动录制
            mMediaRecord!!.startScreenRecord(1, intent, this)
            updateStatus()
        } else if (ACTION_STOP == action) {
            //停止录制
            mMediaRecord!!.stopScreenRecord()
            updateStatus()
        } else if (ACTION_QUERY_STATUS == action) {
            //查询录制状态
            updateStatus()
        } else if (ACTION_PAUSE == action) {
            //暂停录制
            mMediaRecord!!.pauseScreenRecord()
        } else if (ACTION_RESUME == action) {
            //恢复录制
            mMediaRecord!!.resumeScreenRecord()
        }
    }

    /**
     * 广播当前状态
     */
    private fun updateStatus() {
        var  isRecording: Boolean? = false
        var  isPausing: Boolean? = false
        synchronized (sSync) {
            isRecording = mMediaRecord!!.recordStatus
            isPausing = mMediaRecord!!.pauseStatus
        }
        val result = Intent()
        result.action = ACTION_QUERY_STATUS_RESULT
        result.putExtra(EXTRA_QUERY_RESULT_RECORDING, isRecording)
        result.putExtra(EXTRA_QUERY_RESULT_PAUSING, isPausing)
        if (DEBUG) Log.v(TAG, "sendBroadcast:isRecording=$isRecording,isPausing=$isPausing")
        sendBroadcast(result)
    }

    override fun onDestroy() {
        super.onDestroy()
        mMediaRecord = null
    }

    companion object {
        private val DEBUG = false
        private val TAG = "ScreenRecorderService"

        private val BASE = "com.serenegiant.service.ScreenRecorderService."
        val ACTION_START = BASE + "ACTION_START"
        val ACTION_STOP = BASE + "ACTION_STOP"
        val ACTION_PAUSE = BASE + "ACTION_PAUSE"
        val ACTION_RESUME = BASE + "ACTION_RESUME"
        val ACTION_QUERY_STATUS = BASE + "ACTION_QUERY_STATUS"
        val ACTION_QUERY_STATUS_RESULT = BASE + "ACTION_QUERY_STATUS_RESULT"
        val EXTRA_RESULT_CODE = BASE + "EXTRA_RESULT_CODE"
        val EXTRA_QUERY_RESULT_RECORDING = BASE + "EXTRA_QUERY_RESULT_RECORDING"
        val EXTRA_QUERY_RESULT_PAUSING = BASE + "EXTRA_QUERY_RESULT_PAUSING"

        private val sSync = Object()
    }
}
