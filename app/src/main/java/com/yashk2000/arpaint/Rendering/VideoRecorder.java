package com.yashk2000.arpaint.Rendering;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles recording the AR scene to an MP4 video file using MediaRecorder
 * and an OpenGL EGLWindowSurface.
 */
public class VideoRecorder {
  private static final String TAG = "VideoRecorder";

  public interface VideoRecordListener {
    void onRecordingStarted();
    void onRecordingStopped(File videoFile, Uri mediaStoreUri);
    void onError(String errorMessage);
  }

  private MediaRecorder mMediaRecorder;
  private Surface mInputSurface;
  private EGLSurface mRecordingEglSurface = EGL14.EGL_NO_SURFACE;
  private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
  private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;
  private EGLConfig mEglConfig = null;

  private EGLSurface mSavedDrawSurface = EGL14.EGL_NO_SURFACE;
  private EGLSurface mSavedReadSurface = EGL14.EGL_NO_SURFACE;

  private int mVideoWidth = 1280;
  private int mVideoHeight = 720;
  private int mBitRate = 8_000_000;
  private int mFrameRate = 30;

  private volatile boolean mIsRecording = false;
  private volatile boolean mNeedsEglSurfaceCreation = false;
  private volatile boolean mNeedsEglSurfaceTeardown = false;

  private File mCurrentVideoFile;
  private long mRecordingStartTimeMs = 0;
  private VideoRecordListener mListener;
  private final Handler mMainHandler = new Handler(Looper.getMainLooper());

  public VideoRecorder() {}

  public boolean isRecording() {
    return mIsRecording;
  }

  public int getVideoWidth() {
    return mVideoWidth;
  }

  public int getVideoHeight() {
    return mVideoHeight;
  }

  public long getRecordingDurationMs() {
    if (!mIsRecording || mRecordingStartTimeMs == 0) return 0;
    return System.currentTimeMillis() - mRecordingStartTimeMs;
  }

  /**
   * Called on the GL thread to cache EGL state.
   */
  public void onGlThreadInit(EGLDisplay display, EGLContext context, EGLConfig config) {
    this.mEglDisplay = display;
    this.mEglContext = context;
    this.mEglConfig = config;
  }

  /**
   * Start recording session. Call from main/UI thread.
   */
  public synchronized boolean startRecording(
      Context context,
      int screenWidth,
      int screenHeight,
      VideoRecordListener listener) {
    if (mIsRecording) {
      Log.w(TAG, "Recording already in progress");
      return false;
    }
    this.mListener = listener;

    try {
      // Determine optimal video resolution (even numbers, max 1080p, align to 16)
      setupResolution(screenWidth, screenHeight);

      // Create temporary output file
      File outputDir = new File(context.getCacheDir(), "recordings");
      if (!outputDir.exists()) {
        outputDir.mkdirs();
      }
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
      mCurrentVideoFile = new File(outputDir, "ARPaint_" + timeStamp + ".mp4");

      mMediaRecorder = new MediaRecorder();
      mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
      mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
      mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);
      mMediaRecorder.setVideoFrameRate(mFrameRate);
      mMediaRecorder.setVideoEncodingBitRate(mBitRate);
      mMediaRecorder.setOutputFile(mCurrentVideoFile.getAbsolutePath());

      mMediaRecorder.prepare();
      mInputSurface = mMediaRecorder.getSurface();
      mMediaRecorder.start();

      mRecordingStartTimeMs = System.currentTimeMillis();
      mNeedsEglSurfaceCreation = true;
      mIsRecording = true;

      if (mListener != null) {
        mMainHandler.post(() -> mListener.onRecordingStarted());
      }
      Log.i(TAG, "Recording started successfully: " + mCurrentVideoFile.getAbsolutePath());
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Failed to start recording", e);
      releaseMediaRecorder();
      mIsRecording = false;
      if (mListener != null) {
        mMainHandler.post(() -> mListener.onError("Failed to start recorder: " + e.getMessage()));
      }
      return false;
    }
  }

  private void setupResolution(int screenWidth, int screenHeight) {
    if (screenWidth <= 0 || screenHeight <= 0) {
      mVideoWidth = 1280;
      mVideoHeight = 720;
      return;
    }

    // Keep aspect ratio but cap at 1920x1080 or 1080x1920
    float aspect = (float) screenWidth / (float) screenHeight;
    int targetWidth;
    int targetHeight;

    if (screenWidth > screenHeight) {
      targetHeight = Math.min(screenHeight, 1080);
      targetWidth = Math.round(targetHeight * aspect);
    } else {
      targetWidth = Math.min(screenWidth, 1080);
      targetHeight = Math.round(targetWidth / aspect);
    }

    // Must be even and multiple of 16 for broad hardware codec compatibility
    mVideoWidth = (targetWidth / 16) * 16;
    mVideoHeight = (targetHeight / 16) * 16;
    if (mVideoWidth <= 0) mVideoWidth = 720;
    if (mVideoHeight <= 0) mVideoHeight = 1280;
  }

  /**
   * Stop recording session. Call from main/UI thread.
   */
  public synchronized void stopRecording(Context context) {
    if (!mIsRecording) return;

    mIsRecording = false;
    mNeedsEglSurfaceTeardown = true;

    long duration = System.currentTimeMillis() - mRecordingStartTimeMs;

    new Thread(() -> {
      try {
        // Small delay if duration < 1 second to ensure valid MOOV atom
        if (duration < 1000) {
          try {
            Thread.sleep(1000 - duration);
          } catch (InterruptedException ignored) {}
        }

        if (mMediaRecorder != null) {
          try {
            mMediaRecorder.stop();
          } catch (RuntimeException stopException) {
            Log.w(TAG, "MediaRecorder stop failed, video might be empty", stopException);
          }
          releaseMediaRecorder();
        }

        if (mCurrentVideoFile != null && mCurrentVideoFile.exists() && mCurrentVideoFile.length() > 0) {
          Uri savedUri = saveToGallery(context, mCurrentVideoFile);
          Log.i(TAG, "Video successfully saved: " + mCurrentVideoFile.getAbsolutePath() + " uri: " + savedUri);
          if (mListener != null) {
            mMainHandler.post(() -> mListener.onRecordingStopped(mCurrentVideoFile, savedUri));
          }
        } else {
          if (mListener != null) {
            mMainHandler.post(() -> mListener.onError("Recording stopped but no video data was written."));
          }
        }
      } catch (Exception e) {
        Log.e(TAG, "Error stopping recording", e);
        if (mListener != null) {
          mMainHandler.post(() -> mListener.onError("Failed to save video: " + e.getMessage()));
        }
      }
    }).start();
  }

  private void releaseMediaRecorder() {
    if (mMediaRecorder != null) {
      try {
        mMediaRecorder.reset();
        mMediaRecorder.release();
      } catch (Exception ignored) {}
      mMediaRecorder = null;
    }
    if (mInputSurface != null) {
      try {
        mInputSurface.release();
      } catch (Exception ignored) {}
      mInputSurface = null;
    }
  }

  /**
   * Called on the GL thread during onDrawFrame.
   * Checks if an EGLSurface needs to be created or destroyed.
   */
  public void checkGlState() {
    if (mNeedsEglSurfaceCreation) {
      mNeedsEglSurfaceCreation = false;
      createEglSurface();
    }
    if (mNeedsEglSurfaceTeardown) {
      mNeedsEglSurfaceTeardown = false;
      destroyEglSurface();
    }
  }

  private void createEglSurface() {
    if (mInputSurface == null || mEglDisplay == EGL14.EGL_NO_DISPLAY || mEglConfig == null) {
      Log.e(TAG, "Cannot create EGLSurface: missing display, config, or surface");
      return;
    }
    destroyEglSurface();

    int[] surfaceAttribs = new int[] {EGL14.EGL_NONE};
    mRecordingEglSurface =
        EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mInputSurface, surfaceAttribs, 0);

    if (mRecordingEglSurface == EGL14.EGL_NO_SURFACE) {
      Log.e(TAG, "eglCreateWindowSurface failed: " + EGL14.eglGetError());
    } else {
      Log.i(TAG, "Created recording EGL surface successfully");
    }
  }

  private void destroyEglSurface() {
    if (mRecordingEglSurface != EGL14.EGL_NO_SURFACE) {
      EGL14.eglDestroySurface(mEglDisplay, mRecordingEglSurface);
      mRecordingEglSurface = EGL14.EGL_NO_SURFACE;
    }
  }

  /**
   * Begins frame rendering to the recording surface.
   * Call from onDrawFrame on GL thread.
   */
  public boolean beginDraw() {
    if (!mIsRecording || mRecordingEglSurface == EGL14.EGL_NO_SURFACE) {
      return false;
    }

    mSavedDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    mSavedReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

    if (!EGL14.eglMakeCurrent(mEglDisplay, mRecordingEglSurface, mRecordingEglSurface, mEglContext)) {
      Log.e(TAG, "eglMakeCurrent to recording surface failed: " + EGL14.eglGetError());
      return false;
    }

    GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
    return true;
  }

  /**
   * Finishes frame rendering to the recording surface, sets presentation timestamp,
   * swaps buffers, and restores original EGL surface and viewport.
   * Call from onDrawFrame on GL thread.
   */
  public void endDraw(int originalScreenWidth, int originalScreenHeight) {
    if (mRecordingEglSurface == EGL14.EGL_NO_SURFACE) return;

    try {
      EGLExt.eglPresentationTimeANDROID(mEglDisplay, mRecordingEglSurface, System.nanoTime());
      EGL14.eglSwapBuffers(mEglDisplay, mRecordingEglSurface);
    } catch (Exception e) {
      Log.w(TAG, "Error swapping recording buffers", e);
    }

    // Restore original surface and viewport
    EGL14.eglMakeCurrent(mEglDisplay, mSavedDrawSurface, mSavedReadSurface, mEglContext);
    GLES20.glViewport(0, 0, originalScreenWidth, originalScreenHeight);
  }

  /**
   * Saves the recorded MP4 file to MediaStore under Movies/ARPaint so it shows in Gallery.
   */
  private Uri saveToGallery(Context context, File sourceFile) {
    if (context == null || sourceFile == null || !sourceFile.exists()) return null;

    String fileName = sourceFile.getName();
    ContentValues values = new ContentValues();
    values.put(MediaStore.Video.Media.TITLE, fileName);
    values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
    values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
    values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
    values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ARPaint");
      values.put(MediaStore.Video.Media.IS_PENDING, 1);
    }

    ContentResolver resolver = context.getContentResolver();
    Uri collection = MediaStore.Video.Media.getContentUri(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? MediaStore.VOLUME_EXTERNAL_PRIMARY
            : "external");

    Uri itemUri = null;
    try {
      itemUri = resolver.insert(collection, values);
      if (itemUri != null) {
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = resolver.openOutputStream(itemUri)) {
          if (out != null) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
              out.write(buffer, 0, read);
            }
            out.flush();
          }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          values.clear();
          values.put(MediaStore.Video.Media.IS_PENDING, 0);
          resolver.update(itemUri, values, null, null);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to copy video to MediaStore", e);
    }

    // Run MediaScanner to guarantee visibility on all Android versions
    try {
      MediaScannerConnection.scanFile(
          context,
          new String[] {sourceFile.getAbsolutePath()},
          new String[] {"video/mp4"},
          null);
    } catch (Exception ignored) {}

    return itemUri;
  }
}
