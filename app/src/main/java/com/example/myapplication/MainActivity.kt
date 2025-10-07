package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val buffer: ByteBuffer = TODO()
    private lateinit var textureView: TextureView
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private lateinit var imageReader: ImageReader

    private val camManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && textureView.isAvailable) openCamera()
        }

    private fun backCameraId(): String =
        camManager.cameraIdList.first { id ->
            camManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = ensurePermissionAndOpen()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
        }
    }

    private fun ensurePermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) openCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        startBgThread()
        if (!textureView.isAvailable) return

        try {
            camManager.openCamera(backCameraId(), object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { cameraDevice = device; startPreview() }
                override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
            }, bgHandler)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startPreview() {
        val tex = textureView.surfaceTexture ?: return
        val width = 1280
        val height = 720
        tex.setDefaultBufferSize(width, height)

        val previewSurface = Surface(tex)

        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { img ->

                nativeProcessFrame(buffer, img.width, img.height)  // will be used in Step 4
            }
        }, bgHandler)

        val device = cameraDevice ?: return
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Non-deprecated path (API 28+)
            val outputs = listOf(
                OutputConfiguration(previewSurface),
                OutputConfiguration(imageReader.surface)
            )
            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(req.build(), null, bgHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                ContextCompat.getMainExecutor(this),
                callback
            )
            device.createCaptureSession(sessionConfig)
        } else {
            // Fallback for API < 28
            @Suppress("DEPRECATION")
            device.createCaptureSession(
                listOf(previewSurface, imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(req.build(), null, bgHandler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                bgHandler
            )
        }
    }

    private fun startBgThread() {
        if (bgThread != null) return
        bgThread = HandlerThread("camera-bg").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    private fun stopBgThread() {
        bgThread?.quitSafely()
        bgThread?.join()
        bgThread = null
        bgHandler = null
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) ensurePermissionAndOpen()
    }

    override fun onPause() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        stopBgThread()
        super.onPause()
    }

    companion object {

        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("native-lib") } // to be linked in Step 4
    }

    @Suppress("unused")
    private external fun nativeProcessFrame(buffer: ByteBuffer, width: Int, height: Int)
}
