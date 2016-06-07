package material.com.recordsdk.media


import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log

import java.io.IOException

/**
 * Created by zjl on 2016/3/1.
 */
class MediaRecord {

    private var mContext: Context? = null
    //录制状态 录制&停止
    private val isRecording = false
    //录制状态 暂停&继续
    private val isPausing = false
    //屏幕状态
    internal val metrics = mContext!!.resources.displayMetrics
    //录制宽度
    internal var recordWidth = metrics.widthPixels
    //录制高度
    internal var recordHeigtht = metrics.heightPixels
    //录制频率
    internal var density = metrics.densityDpi
    //录制目录
    internal var dictoryPath: String? = null
    //录制文件名
    internal var fileName: String? = null
    //媒体录制管理
    private var mMediaProjectionManager: MediaProjectionManager? = null

    constructor() {

    }

    constructor(mContext: Context) {
        this.mContext = mContext
        mMediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * 设置录制宽高
     * @param width
     * @param height
     */
    fun setSize(width: Int, height: Int) {
        this.recordWidth = width
        this.recordHeigtht = height
    }

    /**
     * 设置录制频率
     * @param density
     */
    fun setDensity(density: Int) {
        this.density = density
    }

    /**
     * 设置录制目录
     * @param dictoryPath
     */
    fun setRecordDictory(dictoryPath: String) {
        this.dictoryPath = dictoryPath
    }

    /**
     * 设置录制文件名
     * @param fileName
     */
    fun setFileName(fileName: String) {
        this.fileName = fileName
    }

    /**
     * 开始录制
     * start screen recording as .mp4 file
     * @param intent
     */
    fun startScreenRecord(resultCode: Int, intent: Intent, mContext: Context) {
        if (DEBUG) Log.v(TAG, "startScreenRecord:sMuxer=" + sMuxer!!)
        synchronized (sSync) {
            if (sMuxer == null) {
                //编码对象不为空
                // get MediaProjection

                val projection = mMediaProjectionManager!!.getMediaProjection(resultCode, intent)
                if (projection != null) {

                    if (DEBUG) Log.v(TAG, "startRecording:")
                    try {
                        //编码对象初始化

                        if (dictoryPath != null) {
                            sMuxer = MediaMuxerWrapper(dictoryPath!!, fileName!!, ".mp4")
                        } else {
                            sMuxer = MediaMuxerWrapper(".mp4")    // if you record audio only, ".m4a" is also OK.
                        }
                        if (true) {
                            // 屏幕录制
                            MediaScreenEncoder(sMuxer!!, mMediaEncoderListener,
                                    projection, recordWidth, recordHeigtht, density)
                        }
                        if (true) {
                            // 声音录制
                            MediaAudioEncoder(sMuxer!!, mMediaEncoderListener)
                        }
                        //编码准备
                        sMuxer!!.prepare()
                        //编码开始
                        sMuxer!!.startRecording()
                    } catch (e: IOException) {
                        Log.e(TAG, "startScreenRecord:", e)
                    }

                }
            }
        }
    }

    /**
     * 停止屏幕录制
     */
    fun stopScreenRecord() {
        if (DEBUG) Log.v(TAG, "stopScreenRecord:sMuxer=" + sMuxer!!)
        synchronized (sSync) {
            if (sMuxer != null) {
                sMuxer!!.stopRecording()
                sMuxer = null
                // you should not wait here
            }
        }
    }

    /**
     * 暂停屏幕录制
     */
    fun pauseScreenRecord() {
        if (DEBUG) Log.v(TAG, "pauseScreenRecord")
        synchronized (sSync) {
            if (sMuxer != null) {
                sMuxer!!.pauseRecording()
            }
        }
    }

    /**
     * 恢复屏幕录制
     */
    fun resumeScreenRecord() {
        if (DEBUG) Log.v(TAG, "resumeScreenRecord")
        synchronized (sSync) {
            if (sMuxer != null) {
                sMuxer!!.resumeRecording()
            }
        }
    }

    val recordStatus: Boolean
        get() = if (sMuxer == null) false else true

    val pauseStatus: Boolean
        get() = sMuxer!!.isPaused

    companion object {
        private val DEBUG = false
        private val REQUEST_CODE_SCREEN_CAPTURE = 1
        //录制单例
        var instance: MediaRecord? = null
        private val TAG = "MediaRecord"
        //异步堵塞
        private val sSync = Object()
        //媒体编码器
        private var sMuxer: MediaMuxerWrapper? = null


        /**
         * 设置单例模式
         * @param mContext
         * *
         * @return
         */
        @Synchronized fun getInstance(mContext: Context): MediaRecord {
            if (instance == null) {
                synchronized (MediaRecord::class.java) {
                    if (instance == null)
                        instance = MediaRecord(mContext)
                }
            }
            return instance!!
        }

        /**
         * 编码回调接口
         */
        private val mMediaEncoderListener = object : MediaEncoder.MediaEncoderListener {
            override fun onPrepared(encoder: MediaEncoder) {
                if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder)
            }

            override fun onStopped(encoder: MediaEncoder) {
                if (DEBUG) Log.v(TAG, "onStopped:encoder=" + encoder)
            }
        }
    }

}
