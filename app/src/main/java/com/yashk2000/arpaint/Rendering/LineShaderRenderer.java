package com.yashk2000.arpaint.Rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import com.yashk2000.arpaint.R;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.vecmath.Vector3f;

/**
 * 3D Volumetric AR Ribbon Renderer:
 * - High-speed ribbon extrusion in clip-space
 * - Per-stroke independent colors and stroke thickness
 * - Silky smooth anti-aliased edge contours
 * - Reusable direct memory buffers preventing native memory leaks
 */
public class LineShaderRenderer {

  private static final String TAG = LineShaderRenderer.class.getSimpleName();
  private static final int FLOATS_PER_POINT = 3; // X,Y,Z
  private static final int BYTES_PER_FLOAT = 4;
  private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;

  private final float[] mModelMatrix = new float[16];
  private final float[] mModelViewMatrix = new float[16];
  private final float[] mModelViewProjectionMatrix = new float[16];

  // Attribute locations
  private int mPositionAttribute = 0;
  private int mPreviousAttribute = 0;
  private int mNextAttribute = 0;
  private int mColorAttribute = 0;
  private int mSideAttribute = 0;
  private int mWidthAttribute = 0;
  private int mCountersAttribute = 0;

  // Uniform locations
  private int mProjectionUniform = 0;
  private int mModelViewUniform = 0;
  private int mResolutionUniform = 0;
  private int mLineWidthUniform = 0;
  private int mLineDepthScaleUniform = 0;
  private int mOpacityUniform = 0;
  private int mNearUniform = 0;
  private int mFarUniform = 0;
  private int mSizeAttenuationUniform = 0;
  private int mVisibilityUniform = 0;
  private int mAlphaTestUniform = 0;

  // Vertex data arrays
  private float[] mPositions;
  private float[] mNext;
  private float[] mPrevious;
  private float[] mColors;
  private float[] mCounters;
  private float[] mSide;
  private float[] mWidth;

  // Cached direct FloatBuffers
  private FloatBuffer mPositionsBuffer;
  private FloatBuffer mNextBuffer;
  private FloatBuffer mPreviousBuffer;
  private FloatBuffer mColorsBuffer;
  private FloatBuffer mSideBuffer;
  private FloatBuffer mWidthBuffer;
  private FloatBuffer mCountersBuffer;
  private int mAllocatedCount = 0;

  // VBO byte offsets
  private int mPositionAddress = 0;
  private int mNextAddress = 0;
  private int mPreviousAddress = 0;
  private int mColorAddress = 0;
  private int mSideAddress = 0;
  private int mWidthAddress = 0;
  private int mCounterAddress = 0;

  private int mNumBytes = 0;
  private int mVbo = 0;
  private int mVboSize = 0;
  private int mProgramName = 0;

  public final AtomicBoolean bNeedsUpdate = new AtomicBoolean(false);
  public float mDrawDistance = 0.125f;

  public LineShaderRenderer() {
    Matrix.setIdentityM(mModelMatrix, 0);
  }

  public void createOnGlThread(Context context) {
    if (context == null) return;
    try {
      int[] buffers = new int[1];
      GLES20.glGenBuffers(1, buffers, 0);
      mVbo = buffers[0];
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
      mVboSize = 0;
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

      int vertexShader =
          ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.line_vert);
      int fragmentShader =
          ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.line_frag);

      mProgramName = GLES20.glCreateProgram();
      GLES20.glAttachShader(mProgramName, vertexShader);
      GLES20.glAttachShader(mProgramName, fragmentShader);
      GLES20.glLinkProgram(mProgramName);

      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(mProgramName, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] != GLES20.GL_TRUE) {
        Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(mProgramName));
        GLES20.glDeleteProgram(mProgramName);
        mProgramName = 0;
        return;
      }

      GLES20.glUseProgram(mProgramName);

      mPositionAttribute = GLES20.glGetAttribLocation(mProgramName, "position");
      mPreviousAttribute = GLES20.glGetAttribLocation(mProgramName, "previous");
      mNextAttribute = GLES20.glGetAttribLocation(mProgramName, "next");
      mColorAttribute = GLES20.glGetAttribLocation(mProgramName, "color");
      mSideAttribute = GLES20.glGetAttribLocation(mProgramName, "side");
      mWidthAttribute = GLES20.glGetAttribLocation(mProgramName, "width");
      mCountersAttribute = GLES20.glGetAttribLocation(mProgramName, "counters");

      mProjectionUniform = GLES20.glGetUniformLocation(mProgramName, "projectionMatrix");
      mModelViewUniform = GLES20.glGetUniformLocation(mProgramName, "modelViewMatrix");
      mResolutionUniform = GLES20.glGetUniformLocation(mProgramName, "resolution");
      mLineWidthUniform = GLES20.glGetUniformLocation(mProgramName, "lineWidth");
      mLineDepthScaleUniform = GLES20.glGetUniformLocation(mProgramName, "lineDepthScale");
      mOpacityUniform = GLES20.glGetUniformLocation(mProgramName, "opacity");
      mNearUniform = GLES20.glGetUniformLocation(mProgramName, "near");
      mFarUniform = GLES20.glGetUniformLocation(mProgramName, "far");
      mSizeAttenuationUniform = GLES20.glGetUniformLocation(mProgramName, "sizeAttenuation");
      mVisibilityUniform = GLES20.glGetUniformLocation(mProgramName, "visibility");
      mAlphaTestUniform = GLES20.glGetUniformLocation(mProgramName, "alphaTest");

      Matrix.setIdentityM(mModelMatrix, 0);
      bNeedsUpdate.set(true);
    } catch (Exception e) {
      Log.e(TAG, "Error in createOnGlThread", e);
    }
  }

  public synchronized void updateStrokes(List<Stroke> strokes) {
    if (strokes == null || strokes.isEmpty()) {
      mNumBytes = 0;
      return;
    }

    int totalPoints = 0;
    for (int i = 0; i < strokes.size(); i++) {
      Stroke stroke = strokes.get(i);
      if (stroke != null && !stroke.getPoints().isEmpty()) {
        int ptCount = Math.max(2, stroke.getPoints().size());
        totalPoints += ptCount * 2 + 2;
      }
    }

    if (totalPoints == 0) {
      mNumBytes = 0;
      return;
    }

    ensureCapacity(totalPoints);

    int offset = 0;
    for (int i = 0; i < strokes.size(); i++) {
      Stroke stroke = strokes.get(i);
      if (stroke != null && !stroke.getPoints().isEmpty()) {
        offset = addLine(stroke.getPoints(), stroke.getColor(), stroke.getLineWidth(), offset);
      }
    }
    mNumBytes = offset;
  }

  private void ensureCapacity(int numPoints) {
    int count = Math.max(1024, mAllocatedCount);
    while (count < numPoints) {
      count = count * 2;
    }

    if (mAllocatedCount < count) {
      mAllocatedCount = count;
      mPositions = new float[count * 3];
      mNext = new float[count * 3];
      mPrevious = new float[count * 3];
      mColors = new float[count * 3];
      mCounters = new float[count];
      mSide = new float[count];
      mWidth = new float[count];

      ByteBuffer bbPos = ByteBuffer.allocateDirect(count * 3 * BYTES_PER_FLOAT);
      bbPos.order(ByteOrder.nativeOrder());
      mPositionsBuffer = bbPos.asFloatBuffer();

      ByteBuffer bbNext = ByteBuffer.allocateDirect(count * 3 * BYTES_PER_FLOAT);
      bbNext.order(ByteOrder.nativeOrder());
      mNextBuffer = bbNext.asFloatBuffer();

      ByteBuffer bbPrev = ByteBuffer.allocateDirect(count * 3 * BYTES_PER_FLOAT);
      bbPrev.order(ByteOrder.nativeOrder());
      mPreviousBuffer = bbPrev.asFloatBuffer();

      ByteBuffer bbCol = ByteBuffer.allocateDirect(count * 3 * BYTES_PER_FLOAT);
      bbCol.order(ByteOrder.nativeOrder());
      mColorsBuffer = bbCol.asFloatBuffer();

      ByteBuffer bbSide = ByteBuffer.allocateDirect(count * BYTES_PER_FLOAT);
      bbSide.order(ByteOrder.nativeOrder());
      mSideBuffer = bbSide.asFloatBuffer();

      ByteBuffer bbWidth = ByteBuffer.allocateDirect(count * BYTES_PER_FLOAT);
      bbWidth.order(ByteOrder.nativeOrder());
      mWidthBuffer = bbWidth.asFloatBuffer();

      ByteBuffer bbCount = ByteBuffer.allocateDirect(count * BYTES_PER_FLOAT);
      bbCount.order(ByteOrder.nativeOrder());
      mCountersBuffer = bbCount.asFloatBuffer();
    }
  }

  private int addLine(List<Vector3f> line, Vector3f color, float strokeWidth, int offset) {
    if (line == null || line.isEmpty()) return offset;

    List<Vector3f> points = line;
    if (line.size() == 1) {
      Vector3f p = line.get(0);
      points = new java.util.ArrayList<>(2);
      points.add(p);
      points.add(new Vector3f(p.x + 0.0005f, p.y + 0.0005f, p.z));
    }

    int lineSize = points.size();
    int ii = offset;

    for (int i = 0; i < lineSize; i++) {
      int iGood = i;
      int i_m_1 = Math.max(0, iGood - 1);
      int i_p_1 = Math.min(lineSize - 1, iGood + 1);
      float c = (lineSize > 1) ? ((float) i / (float) (lineSize - 1)) : 0f;

      Vector3f current = points.get(iGood);
      Vector3f previous = points.get(i_m_1);
      Vector3f next = points.get(i_p_1);

      if (current == null || previous == null || next == null) continue;

      if (i == 0) {
        setMemory(ii++, current, previous, next, c, strokeWidth, 1f, color);
      }

      setMemory(ii++, current, previous, next, c, strokeWidth, 1f, color);
      setMemory(ii++, current, previous, next, c, strokeWidth, -1f, color);

      if (i == lineSize - 1) {
        setMemory(ii++, current, previous, next, c, strokeWidth, -1f, color);
      }
    }
    return ii;
  }

  private void setMemory(
      int index,
      Vector3f pos,
      Vector3f prev,
      Vector3f next,
      float counter,
      float width,
      float side,
      Vector3f color) {
    if (index * 3 + 2 >= mPositions.length) return;

    mPositions[index * 3] = pos.x;
    mPositions[index * 3 + 1] = pos.y;
    mPositions[index * 3 + 2] = pos.z;

    mNext[index * 3] = next.x;
    mNext[index * 3 + 1] = next.y;
    mNext[index * 3 + 2] = next.z;

    mPrevious[index * 3] = prev.x;
    mPrevious[index * 3 + 1] = prev.y;
    mPrevious[index * 3 + 2] = prev.z;

    if (color != null) {
      mColors[index * 3] = color.x;
      mColors[index * 3 + 1] = color.y;
      mColors[index * 3 + 2] = color.z;
    } else {
      mColors[index * 3] = 1f;
      mColors[index * 3 + 1] = 1f;
      mColors[index * 3 + 2] = 1f;
    }

    mCounters[index] = counter;
    mSide[index] = side;
    mWidth[index] = width;
  }

  public void clear() {
    bNeedsUpdate.set(true);
  }

  public synchronized void upload() {
    bNeedsUpdate.set(false);

    if (mNumBytes <= 0 || mPositions == null || mVbo == 0) {
      return;
    }

    try {
      mPositionsBuffer.clear();
      mPositionsBuffer.put(mPositions, 0, mNumBytes * 3);
      mPositionsBuffer.position(0);

      mNextBuffer.clear();
      mNextBuffer.put(mNext, 0, mNumBytes * 3);
      mNextBuffer.position(0);

      mPreviousBuffer.clear();
      mPreviousBuffer.put(mPrevious, 0, mNumBytes * 3);
      mPreviousBuffer.position(0);

      mColorsBuffer.clear();
      mColorsBuffer.put(mColors, 0, mNumBytes * 3);
      mColorsBuffer.position(0);

      mSideBuffer.clear();
      mSideBuffer.put(mSide, 0, mNumBytes);
      mSideBuffer.position(0);

      mWidthBuffer.clear();
      mWidthBuffer.put(mWidth, 0, mNumBytes);
      mWidthBuffer.position(0);

      mCountersBuffer.clear();
      mCountersBuffer.put(mCounters, 0, mNumBytes);
      mCountersBuffer.position(0);

      mPositionAddress = 0;
      mNextAddress = mPositionAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
      mPreviousAddress = mNextAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
      mColorAddress = mPreviousAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
      mSideAddress = mColorAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
      mWidthAddress = mSideAddress + mNumBytes * BYTES_PER_FLOAT;
      mCounterAddress = mWidthAddress + mNumBytes * BYTES_PER_FLOAT;
      mVboSize = mCounterAddress + mNumBytes * BYTES_PER_FLOAT;

      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);

      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mPositionAddress, mNumBytes * 3 * BYTES_PER_FLOAT, mPositionsBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mNextAddress, mNumBytes * 3 * BYTES_PER_FLOAT, mNextBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mPreviousAddress, mNumBytes * 3 * BYTES_PER_FLOAT, mPreviousBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mColorAddress, mNumBytes * 3 * BYTES_PER_FLOAT, mColorsBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mSideAddress, mNumBytes * BYTES_PER_FLOAT, mSideBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mWidthAddress, mNumBytes * BYTES_PER_FLOAT, mWidthBuffer);
      GLES20.glBufferSubData(
          GLES20.GL_ARRAY_BUFFER, mCounterAddress, mNumBytes * BYTES_PER_FLOAT, mCountersBuffer);

      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    } catch (Exception e) {
      Log.e(TAG, "Error uploading line vertex buffers", e);
    }
  }

  public void draw(
      float[] cameraView,
      float[] cameraPerspective,
      float screenWidth,
      float screenHeight,
      float nearClip,
      float farClip) {
    if (mNumBytes <= 0 || mProgramName == 0 || mVbo == 0) {
      return;
    }

    Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0);
    Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0);

    GLES20.glUseProgram(mProgramName);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

    GLES20.glVertexAttribPointer(
        mPositionAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPositionAddress);
    GLES20.glVertexAttribPointer(
        mPreviousAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPreviousAddress);
    GLES20.glVertexAttribPointer(
        mNextAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mNextAddress);
    GLES20.glVertexAttribPointer(
        mColorAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mColorAddress);
    GLES20.glVertexAttribPointer(
        mSideAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mSideAddress);
    GLES20.glVertexAttribPointer(
        mWidthAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mWidthAddress);
    GLES20.glVertexAttribPointer(
        mCountersAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mCounterAddress);

    GLES20.glUniformMatrix4fv(mModelViewUniform, 1, false, mModelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(mProjectionUniform, 1, false, cameraPerspective, 0);
    GLES20.glUniform2f(mResolutionUniform, screenWidth, screenHeight);
    GLES20.glUniform1f(mLineWidthUniform, 0.01f);
    GLES20.glUniform1f(mLineDepthScaleUniform, 10.0f);
    GLES20.glUniform1f(mOpacityUniform, 1.0f);
    GLES20.glUniform1f(mNearUniform, nearClip);
    GLES20.glUniform1f(mFarUniform, farClip);
    GLES20.glUniform1f(mSizeAttenuationUniform, 1.0f);
    GLES20.glUniform1f(mVisibilityUniform, 1.0f);
    GLES20.glUniform1f(mAlphaTestUniform, 0.0f);

    GLES20.glEnableVertexAttribArray(mPositionAttribute);
    GLES20.glEnableVertexAttribArray(mPreviousAttribute);
    GLES20.glEnableVertexAttribArray(mNextAttribute);
    GLES20.glEnableVertexAttribArray(mColorAttribute);
    GLES20.glEnableVertexAttribArray(mSideAttribute);
    GLES20.glEnableVertexAttribArray(mWidthAttribute);
    GLES20.glEnableVertexAttribArray(mCountersAttribute);

    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    GLES20.glDisable(GLES20.GL_CULL_FACE);
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glDepthFunc(GLES20.GL_LEQUAL);
    GLES20.glDepthMask(true);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mNumBytes);

    GLES20.glDisable(GLES20.GL_BLEND);

    GLES20.glDisableVertexAttribArray(mCountersAttribute);
    GLES20.glDisableVertexAttribArray(mWidthAttribute);
    GLES20.glDisableVertexAttribArray(mSideAttribute);
    GLES20.glDisableVertexAttribArray(mColorAttribute);
    GLES20.glDisableVertexAttribArray(mNextAttribute);
    GLES20.glDisableVertexAttribArray(mPreviousAttribute);
    GLES20.glDisableVertexAttribArray(mPositionAttribute);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
  }
}
