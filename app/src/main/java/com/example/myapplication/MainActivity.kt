package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.gl.EdgeRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: EdgeRenderer

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private lateinit var imageReader: ImageReader

    private lateinit var outRgba: ByteBuffer

    private val camManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
        }

    private fun backCameraId(): String =
        camManager.cameraIdList.first { id ->
            camManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edge_view)

        glView = findViewById(R.id.glView)
        renderer = EdgeRenderer()
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        ensurePermissionAndOpen()
    }

    private fun ensurePermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) openCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        startBgThread()

        camManager.openCamera(
            backCameraId(),
            object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { cameraDevice = device; startPreview() }
                override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); cameraDevice = null }
            },
            bgHandler
        )
    }

    private fun startPreview() {
        val width = 1280
        val height = 720

        outRgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())

        // 🔹 Use deeper ImageReader queue to avoid dropped frames
        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3)
        imageReader.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { img ->
                val y = img.planes[0]
                nativeProcessFrame(
                    y.buffer,
                    y.rowStride,
                    y.pixelStride,
                    outRgba,
                    img.width,
                    img.height
                )
                renderer.updateFrame(outRgba, img.width, img.height)
                glView.requestRender()
            }
        }, bgHandler)

        val device = cameraDevice ?: return

        // 🔹 Build capture request with stable FPS & reduced latency
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(imageReader.surface)

            val chars = camManager.getCameraCharacteristics(backCameraId())
            val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val preferred = ranges?.filter { it.lower >= 24 }?.maxByOrNull { it.upper }
                ?: Range(30, 30)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, preferred)

            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
            set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        // 🔹 Create capture session
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val outputs = listOf(OutputConfiguration(imageReader.surface))
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
            @Suppress("DEPRECATION")
            device.createCaptureSession(
                listOf(imageReader.surface),
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
        glView.onResume()
    }

    override fun onPause() {
        glView.onPause()
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        stopBgThread()
        super.onPause()
    }

    companion object {
        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("native-lib")
        }
    }

    private external fun nativeProcessFrame(
        yPlane: ByteBuffer,
        yRowStride: Int,
        yPixelStride: Int,
        outRgba: ByteBuffer,
        width: Int,
        height: Int
    )
}
