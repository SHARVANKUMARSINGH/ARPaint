package com.yashk2000.arpaint

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.yashk2000.arpaint.Rendering.*
import com.yashk2000.arpaint.Utils.DisplayRotationHelper
import com.yashk2000.arpaint.Utils.Settings
import com.yashk2000.arpaint.ui.ARPaintScreen
import com.yashk2000.arpaint.ui.ARTrackingState
import com.yashk2000.arpaint.ui.theme.ARPaintTheme
import java.io.File
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import javax.vecmath.Vector2f
import javax.vecmath.Vector3f

class MainActivity : ComponentActivity(), GLSurfaceView.Renderer {

    private val TAG = "MainActivity"

    private lateinit var surfaceView: GLSurfaceView
    private var session: Session? = null
    private var installRequested = false
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var sharedPreferences: SharedPreferences

    @Volatile
    private var paused = false
    private var screenWidth = 0
    private var screenHeight = 0

    private val backgroundRenderer = BackgroundRenderer()
    private val lineShaderRenderer = LineShaderRenderer()
    private val videoRecorder = VideoRecorder()

    private var frame: Frame? = null
    private val isTracking = AtomicBoolean(false)
    private val zeroMatrix = FloatArray(16)
    private var lastPoint = Vector3f(0f, 0f, 0f)

    private val strokesLock = Any()
    private val strokes = ArrayList<ArrayList<Vector3f>>()

    private val projmtx = FloatArray(16)
    private val viewmtx = FloatArray(16)

    private val touchDown = AtomicBoolean(false)
    private var lastFramePosition: FloatArray? = null
    private val newStroke = AtomicBoolean(false)
    private val lastTouch = AtomicReference<Vector2f>()
    private val reCenterView = AtomicBoolean(false)
    private val undoMove = AtomicBoolean(false)
    private val clearDrawing = AtomicBoolean(false)

    private var biquadFilter = BiquadFilter(0.1)

    // UI state observable by Jetpack Compose
    private val trackingStateCompose = mutableStateOf(ARTrackingState.SEARCHING)
    private val strokeCountCompose = mutableIntStateOf(0)
    private val currentColorCompose = mutableStateOf(Color.White)
    private val currentThicknessCompose = mutableFloatStateOf(0.33f)
    private val isRecordingCompose = mutableStateOf(false)
    private val savedVideoFileCompose = mutableStateOf<File?>(null)
    private val savedVideoUriCompose = mutableStateOf<android.net.Uri?>(null)
    private val errorMessageCompose = mutableStateOf<String?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            resumeArSession()
        } else {
            Toast.makeText(this, "Camera permission is required for AR Paint", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        displayRotationHelper = DisplayRotationHelper(this)
        Matrix.setIdentityM(zeroMatrix, 0)

        // Load saved preferences
        val savedWidth = sharedPreferences.getFloat("LineWidthFloat", 0.33f)
        currentThicknessCompose.value = savedWidth
        Settings.setLineWidth(savedWidth)

        val savedColorInt = sharedPreferences.getInt("StrokeColorInt", android.graphics.Color.WHITE)
        currentColorCompose.value = Color(savedColorInt)
        Settings.setColorFromInt(savedColorInt)

        // Initialize GLSurfaceView
        surfaceView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@MainActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }

        // Set Compose UI Content
        setContent {
            ARPaintTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { surfaceView },
                        modifier = Modifier.fillMaxSize()
                    )

                    ARPaintScreen(
                        trackingState = trackingStateCompose.value,
                        strokeCount = strokeCountCompose.intValue,
                        currentColor = currentColorCompose.value,
                        currentThickness = currentThicknessCompose.floatValue,
                        isRecording = isRecordingCompose.value,
                        onColorChanged = { newColor ->
                            currentColorCompose.value = newColor
                            Settings.setColorFromInt(newColor.toArgb())
                            lineShaderRenderer.bNeedsUpdate.set(true)
                            sharedPreferences.edit().putInt("StrokeColorInt", newColor.toArgb()).apply()
                        },
                        onThicknessChanged = { newThickness ->
                            currentThicknessCompose.floatValue = newThickness
                            Settings.setLineWidth(newThickness)
                            lineShaderRenderer.bNeedsUpdate.set(true)
                            sharedPreferences.edit().putFloat("LineWidthFloat", newThickness).apply()
                        },
                        onUndo = {
                            undoMove.set(true)
                        },
                        onClear = {
                            clearDrawing.set(true)
                        },
                        onToggleRecording = {
                            toggleRecording()
                        },
                        savedVideoFile = savedVideoFileCompose.value,
                        savedVideoUri = savedVideoUriCompose.value,
                        onDismissVideoSaved = {
                            savedVideoFileCompose.value = null
                            savedVideoUriCompose.value = null
                        },
                        errorMessage = errorMessageCompose.value,
                        onDismissError = {
                            errorMessageCompose.value = null
                        }
                    )
                }
            }
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(Vector2f(event.x, event.y))
                touchDown.set(true)
                newStroke.set(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouch.set(Vector2f(event.x, event.y))
                touchDown.set(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchDown.set(false)
                lastTouch.set(Vector2f(event.x, event.y))
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        paused = false
        surfaceView.onResume()
        displayRotationHelper.onResume()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            resumeArSession()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun resumeArSession() {
        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }

                session = Session(this)
                val config = Config(session)
                if (!session!!.isSupported(config)) {
                    Log.w(TAG, "ARCore config not supported on this device")
                }
                session!!.configure(config)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install Google Play Services for AR"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "ARCore installation was declined"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = "This device does not support AR"
                exception = e
            } catch (e: Exception) {
                message = "Failed to create AR session: ${e.message}"
                exception = e
            }

            if (message != null) {
                Log.e(TAG, "AR Session initialization error: $message", exception)
                errorMessageCompose.value = message
                return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
            errorMessageCompose.value = "Camera not available. Please try restarting the app."
            session = null
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
        if (videoRecorder.isRecording) {
            videoRecorder.stopRecording(this)
            isRecordingCompose.value = false
        }
        displayRotationHelper.onPause()
        surfaceView.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
        session = null
    }

    private fun toggleRecording() {
        if (videoRecorder.isRecording) {
            videoRecorder.stopRecording(this)
            isRecordingCompose.value = false
        } else {
            val started = videoRecorder.startRecording(
                this,
                screenWidth,
                screenHeight,
                object : VideoRecorder.VideoRecordListener {
                    override fun onRecordingStarted() {
                        isRecordingCompose.value = true
                    }

                    override fun onRecordingStopped(videoFile: File, mediaStoreUri: android.net.Uri?) {
                        isRecordingCompose.value = false
                        savedVideoFileCompose.value = videoFile
                        savedVideoUriCompose.value = mediaStoreUri
                    }

                    override fun onError(errorMessage: String) {
                        isRecordingCompose.value = false
                        errorMessageCompose.value = errorMessage
                    }
                }
            )
            if (started) {
                isRecordingCompose.value = true
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Initialize EGL state in video recorder
        val display = EGL14.eglGetCurrentDisplay()
        val context = EGL14.eglGetCurrentContext()
        val numConfigs = IntArray(1)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)
        videoRecorder.onGlThreadInit(display, context, configs[0])

        backgroundRenderer.createOnGlThread(this)
        lineShaderRenderer.createOnGlThread(this)

        session?.let {
            it.setCameraTextureName(backgroundRenderer.textureId)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        displayRotationHelper.onSurfaceChanged(width, height)
        screenWidth = width
        screenHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        if (paused) return

        videoRecorder.checkGlState()

        updateScene()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val currentFrame = frame ?: return

        // 1. Draw to normal screen (GLSurfaceView)
        backgroundRenderer.draw(currentFrame)
        if (currentFrame.camera.trackingState == TrackingState.TRACKING) {
            lineShaderRenderer.draw(
                viewmtx,
                projmtx,
                screenWidth.toFloat(),
                screenHeight.toFloat(),
                Settings.getNearClip(),
                Settings.getFarClip()
            )
        }

        // 2. Mirror to Video Recorder if recording is active
        if (videoRecorder.isRecording) {
            if (videoRecorder.beginDraw()) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                backgroundRenderer.draw(currentFrame)
                if (currentFrame.camera.trackingState == TrackingState.TRACKING) {
                    lineShaderRenderer.draw(
                        viewmtx,
                        projmtx,
                        videoRecorder.videoWidth.toFloat(),
                        videoRecorder.videoHeight.toFloat(),
                        Settings.getNearClip(),
                        Settings.getFarClip()
                    )
                }
                videoRecorder.endDraw(screenWidth, screenHeight)
            }
        }
    }

    private fun updateScene() {
        val currentSession = session ?: return

        displayRotationHelper.updateSessionIfNeeded(currentSession)

        try {
            currentSession.setCameraTextureName(backgroundRenderer.textureId)
            val updatedFrame = currentSession.update()
            frame = updatedFrame
            val camera = updatedFrame.camera

            val state = camera.trackingState
            val trackingNow = (state == TrackingState.TRACKING)

            if (trackingNow != isTracking.get()) {
                isTracking.set(trackingNow)
                runOnUiThread {
                    trackingStateCompose.value = when (state) {
                        TrackingState.TRACKING -> ARTrackingState.TRACKING
                        TrackingState.PAUSED -> ARTrackingState.PAUSED
                        else -> ARTrackingState.SEARCHING
                    }
                }
            }

            camera.getProjectionMatrix(projmtx, 0, Settings.getNearClip(), Settings.getFarClip())
            camera.getViewMatrix(viewmtx, 0)

            val position = FloatArray(3)
            camera.pose.getTranslation(position, 0)

            lastFramePosition?.let { lastPos ->
                val dist = Vector3f(position[0], position[1], position[2])
                dist.sub(Vector3f(lastPos[0], lastPos[1], lastPos[2]))
                if (dist.length() > 0.15f) {
                    touchDown.set(false)
                }
            }
            lastFramePosition = position

            Matrix.multiplyMM(viewmtx, 0, viewmtx, 0, zeroMatrix, 0)

            // Touch handling
            val touch = lastTouch.get()
            if (touch != null) {
                if (newStroke.get()) {
                    newStroke.set(false)
                    addStroke(touch)
                    lineShaderRenderer.bNeedsUpdate.set(true)
                } else if (touchDown.get()) {
                    addPoint(touch)
                    lineShaderRenderer.bNeedsUpdate.set(true)
                }
            }

            if (reCenterView.get()) {
                reCenterView.set(false)
                getCalibrationMatrix()?.let { calib ->
                    System.arraycopy(calib, 0, zeroMatrix, 0, 16)
                }
            }

            if (undoMove.get()) {
                undoMove.set(false)
                synchronized(strokesLock) {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.size - 1)
                        lineShaderRenderer.bNeedsUpdate.set(true)
                    }
                }
                updateStrokeCountUi()
            }

            if (clearDrawing.get()) {
                clearDrawing.set(false)
                synchronized(strokesLock) {
                    strokes.clear()
                    lineShaderRenderer.clear()
                    lineShaderRenderer.bNeedsUpdate.set(true)
                }
                updateStrokeCountUi()
            }

            if (lineShaderRenderer.bNeedsUpdate.get()) {
                lineShaderRenderer.setColor(Settings.getColor())
                lineShaderRenderer.mDrawDistance = Settings.getStrokeDrawDistance()
                lineShaderRenderer.setDistanceScale(0.0f)
                lineShaderRenderer.setLineWidth(Settings.getLineWidth())

                synchronized(strokesLock) {
                    lineShaderRenderer.updateStrokes(strokes)
                }
                lineShaderRenderer.upload()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during AR frame update", e)
        }
    }

    private fun addStroke(touchPoint: Vector2f) {
        val newPoint = LineUtils.GetWorldCoords(
            touchPoint,
            screenWidth.toFloat(),
            screenHeight.toFloat(),
            projmtx,
            viewmtx
        ) ?: return

        biquadFilter = BiquadFilter(0.1)
        biquadFilter.reset(newPoint)
        lastPoint = biquadFilter.update(newPoint)

        synchronized(strokesLock) {
            val stroke = ArrayList<Vector3f>()
            stroke.add(lastPoint)
            strokes.add(stroke)
        }
        updateStrokeCountUi()
    }

    private fun addPoint(touchPoint: Vector2f) {
        val newPoint = LineUtils.GetWorldCoords(
            touchPoint,
            screenWidth.toFloat(),
            screenHeight.toFloat(),
            projmtx,
            viewmtx
        ) ?: return

        if (LineUtils.distanceCheck(newPoint, lastPoint)) {
            val smoothed = biquadFilter.update(newPoint)
            lastPoint = smoothed

            synchronized(strokesLock) {
                if (strokes.isEmpty()) {
                    val stroke = ArrayList<Vector3f>()
                    stroke.add(lastPoint)
                    strokes.add(stroke)
                } else {
                    strokes[strokes.size - 1].add(lastPoint)
                }
            }
        }
    }

    private fun updateStrokeCountUi() {
        val count = synchronized(strokesLock) { strokes.size }
        runOnUiThread {
            strokeCountCompose.intValue = count
        }
    }

    private fun getCalibrationMatrix(): FloatArray? {
        val currentFrame = frame ?: return null
        val t = FloatArray(3)
        val m = FloatArray(16)

        currentFrame.camera.pose.getTranslation(t, 0)
        val z = currentFrame.camera.pose.zAxis
        val zAxis = Vector3f(z[0], z[1], z[2])
        zAxis.y = 0f
        if (zAxis.lengthSquared() > 1e-6f) {
            zAxis.normalize()
        }

        val rotate = Math.atan2(zAxis.x.toDouble(), zAxis.z.toDouble())

        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, t[0], t[1], t[2])
        Matrix.rotateM(m, 0, Math.toDegrees(rotate).toFloat(), 0f, 1f, 0f)
        return m
    }
}
