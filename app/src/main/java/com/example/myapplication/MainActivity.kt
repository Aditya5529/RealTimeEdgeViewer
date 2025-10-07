package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private val camManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }

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
        startBgThread()
        if (!textureView.isAvailable) return
        camManager.openCamera(backCameraId(), object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) { cameraDevice = device; startPreview() }
            override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
            override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
        }, bgHandler)
    }

    private fun startPreview() {
        val tex = textureView.surfaceTexture ?: return
        tex.setDefaultBufferSize(1280, 720)
        val surface = Surface(tex)

        val device = cameraDevice ?: return
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                session.setRepeatingRequest(req.build(), null, bgHandler)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, bgHandler)
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
}
