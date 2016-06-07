package material.com.muxer.screenrecordingsample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import android.view.View


import material.com.muxer.R
import material.com.muxer.service.ScreenRecorderService

import java.lang.ref.WeakReference

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Button

import android.widget.Toast

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var mReceiver: MyBroadcastReceiver? = null

    private var mRecrodBtn: Button? = null
    private var mPausepBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.v(TAG, "onCreate:")
        setContentView(R.layout.activity_main)

        mRecrodBtn = findViewById(R.id.record_btn) as Button
        mPausepBtn = findViewById(R.id.stop_btn) as Button
        mRecrodBtn!!.setOnClickListener(this)
        mPausepBtn!!.setOnClickListener(this)

        updateRecording(false, false)
        if (mReceiver == null)
            mReceiver = MyBroadcastReceiver(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.record_btn -> if (!isRecording) {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val permissionIntent = manager.createScreenCaptureIntent()
                startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE)
            } else {
                val intent = Intent(this@MainActivity, ScreenRecorderService::class.java)
                intent.action = ScreenRecorderService.ACTION_STOP
                startService(intent)
            }
            R.id.stop_btn -> if (!isPausing) {
                val intent = Intent(this@MainActivity, ScreenRecorderService::class.java)
                intent.action = ScreenRecorderService.ACTION_PAUSE
                startService(intent)
            } else {
                val intent = Intent(this@MainActivity, ScreenRecorderService::class.java)
                intent.action = ScreenRecorderService.ACTION_RESUME
                startService(intent)
            }
            else -> {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.v(TAG, "onResume:")
        val intentFilter = IntentFilter()
        intentFilter.addAction(ScreenRecorderService.ACTION_QUERY_STATUS_RESULT)
        registerReceiver(mReceiver, intentFilter)
        queryRecordingStatus()
    }

    override fun onPause() {
        if (DEBUG) Log.v(TAG, "onPause:")
        unregisterReceiver(mReceiver)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (DEBUG) Log.v(TAG, "onActivityResult:resultCode=$resultCode,data=$data")
        super.onActivityResult(requestCode, resultCode, data)
        if (REQUEST_CODE_SCREEN_CAPTURE == requestCode) {
            if (resultCode != Activity.RESULT_OK) {
                // when no permission
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show()
                return
            }
            startScreenRecorder(resultCode, data)
        }
    }

    private fun queryRecordingStatus() {
        if (DEBUG) Log.v(TAG, "queryRecording:")
        val intent = Intent(this, ScreenRecorderService::class.java)
        intent.action = ScreenRecorderService.ACTION_QUERY_STATUS
        startService(intent)
    }

    private fun startScreenRecorder(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenRecorderService::class.java)
        intent.action = ScreenRecorderService.ACTION_START
        intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
        intent.putExtras(data)
        startService(intent)
    }

    private fun updateRecording(isRecording: Boolean, isPausing: Boolean) {
        if (DEBUG) Log.v(TAG, "updateRecording:isRecording=$isRecording,isPausing=$isPausing")

        if (isRecording == false) {
            mRecrodBtn!!.text = "录制"
            mPausepBtn!!.text = "暂停"
            mPausepBtn!!.isEnabled = false
        } else {
            mRecrodBtn!!.text = "停止"
            mPausepBtn!!.isEnabled = true
            if (isPausing == false) {
                mPausepBtn!!.text = "暂停"
            } else {
                mPausepBtn!!.text = "继续"
            }
        }
    }

    private class MyBroadcastReceiver(parent: MainActivity) : BroadcastReceiver() {
        private val mWeakParent: WeakReference<MainActivity>

        init {
            mWeakParent = WeakReference(parent)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (DEBUG) Log.v(TAG, "onReceive:" + intent)
            val action = intent.action
            if (ScreenRecorderService.ACTION_QUERY_STATUS_RESULT == action) {
                isRecording = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_RECORDING, false)
                isPausing = intent.getBooleanExtra(ScreenRecorderService.EXTRA_QUERY_RESULT_PAUSING, false)

                val parent = mWeakParent.get()
                parent?.updateRecording(isRecording, isPausing)
            }
        }
    }

    companion object {
        private val DEBUG = false
        private val TAG = "MainActivity"

        private val REQUEST_CODE_SCREEN_CAPTURE = 1

        private var isRecording = false
        private var isPausing = false
    }
}
