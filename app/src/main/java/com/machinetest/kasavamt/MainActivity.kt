package com.machinetest.kasavamt

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var recordButton: TextView
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var mediaRecorder: MediaRecorder
    private var camera: Camera? = null
    private lateinit var timerText: TextView

    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentFile: File? = null
    private var fileIndex = 0
    private var startTime: Long = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        recordButton = findViewById(R.id.recordButton)
        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
        timerText = findViewById(R.id.timerText)
        handleEvents()
    }

    private fun handleEvents() {
        timerText.text = "Click to Record"
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (checkPermissions()) {
                    startCameraPreview()
                    startRecording()
                } else {
                    requestPermissions()
                }
            }
        }
    }

    private fun startCameraPreview() {
        camera = Camera.open()
        camera?.setDisplayOrientation(90)  // Adjust orientation to match device
        try {
            camera?.setPreviewDisplay(surfaceHolder)
            val parameters = camera?.parameters
            parameters?.focusMode =
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO  // Stabilize focus
            parameters?.setPreviewSize(1280, 720)  // Match the video recording size
            camera?.parameters = parameters
            camera?.startPreview()
        } catch (e: Exception) {
            Log.e("Camera", "Error setting camera preview: ${e.message}")
            camera?.release()
            camera = null
        }
    }

    private fun startRecording() {
        isRecording = true
        recordButton.text = "Stop Recording"
        startTime = System.currentTimeMillis()
        updateTimer()
        prepareRecorder()
        mediaRecorder.start()

        handler.postDelayed(::switchFile, 600000)  // Switch file every 10 seconds
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.text = "Start Recording"
        timerText.text = "Click to Record"
        try {
            mediaRecorder.stop()  // Stop and finalize the recording
            Log.d("MediaRecorder", "Recording stopped and finalized successfully.")
        } catch (e: Exception) {
            Log.e("MediaRecorder", "Error stopping recorder: ${e.message}")
        } finally {
            mediaRecorder.reset()  // Ensure the recorder is reset for future use
            camera?.lock()  // Lock camera for other applications or future recordings
            handler.removeCallbacksAndMessages(null)  // Cancel any scheduled tasks
        }
    }

    private fun updateTimer() {
        if (isRecording) {
            val elapsedTime = System.currentTimeMillis() - startTime
            val seconds = (elapsedTime / 1000) % 60
            val minutes = (elapsedTime / (1000 * 60)) % 60
            val hours = (elapsedTime / (1000 * 60 * 60))

            val time = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            timerText.text = time

            // Update the timer every second
            handler.postDelayed(::updateTimer, 1000)
        }
    }


    private fun switchFile() {
        if (isRecording) {
            try {
                mediaRecorder.stop()
            } catch (e: Exception) {
                Log.e("MediaRecorder", "Error stopping recorder during switch: ${e.message}")
            }
            mediaRecorder.reset()
            prepareRecorder()
            mediaRecorder.start()
            handler.postDelayed(::switchFile, 600000)  // Schedule next switch
        }
    }

    private fun getOutputFileUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "Movies/MyAppVideos"
            )  // Save in Movies/MyAppVideos
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    private fun prepareRecorder() {
        mediaRecorder = MediaRecorder()
        camera?.unlock()
        mediaRecorder.setCamera(camera)

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoSize(1280, 720)
        mediaRecorder.setAudioSamplingRate(44100)
        mediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024)  // 2 Mbps
        mediaRecorder.setAudioEncodingBitRate(64000)  // 64 Kbps
        mediaRecorder.setVideoFrameRate(30)  // 30 FPS for smoother video


        val videoUri = getOutputFileUri()
        if (videoUri != null) {
            val pfd = contentResolver.openFileDescriptor(videoUri, "w")  // Open in write mode
            mediaRecorder.setOutputFile(pfd?.fileDescriptor)  // Use the file descriptor
            Log.d("MediaRecorder", "Video saved at URI: $videoUri")
        }

        mediaRecorder.setPreviewDisplay(surfaceHolder.surface)

        try {
            mediaRecorder.prepare()
        } catch (e: IOException) {
            Log.e("MediaRecorder", "Error preparing recorder: ${e.message}")
        }
    }


    private fun getOutputFile(): File {
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return File("")
        if (!outputDir.exists()) {
            outputDir.mkdirs()  // Create the directory if it doesn't exist
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(outputDir, "video_${timestamp}.mp4")
    }


    private fun checkPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audioPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val storagePermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                audioPermission == PackageManager.PERMISSION_GRANTED &&
                storagePermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            101
        )
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCameraPreview()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

}


