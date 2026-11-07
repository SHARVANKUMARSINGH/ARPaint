package com.yashk2000.arpaint.Rendering;

import android.opengl.Matrix;
import com.yashk2000.arpaint.Utils.Settings;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

public class LineUtils {

  public static float map(
      float value,
      float inputMin,
      float inputMax,
      float outputMin,
      float outputMax,
      boolean clamp) {
    if (inputMax == inputMin) {
      return outputMin;
    }
    float outVal =
        ((value - inputMin) / (inputMax - inputMin) * (outputMax - outputMin) + outputMin);

    if (clamp) {
      if (outputMax < outputMin) {
        if (outVal < outputMax) outVal = outputMax;
        else if (outVal > outputMin) outVal = outputMin;
      } else {
        if (outVal > outputMax) outVal = outputMax;
        else if (outVal < outputMin) outVal = outputMin;
      }
    }
    return outVal;
  }

  public static float lerp(float start, float stop, float amt) {
    return start + (stop - start) * amt;
  }

  public static Vector3f GetWorldCoords(
      Vector2f touchPoint,
      float screenWidth,
      float screenHeight,
      float[] projectionMatrix,
      float[] viewMatrix) {
    if (touchPoint == null || screenWidth <= 0 || screenHeight <= 0
        || projectionMatrix == null || viewMatrix == null) {
      return null;
    }
    Ray touchRay = projectRay(touchPoint, screenWidth, screenHeight, projectionMatrix, viewMatrix);
    if (touchRay == null || touchRay.origin == null || touchRay.direction == null) {
      return null;
    }
    Vector3f result = new Vector3f(touchRay.origin);
    Vector3f dir = new Vector3f(touchRay.direction);
    dir.scale(Settings.getStrokeDrawDistance());
    result.add(dir);
    return result;
  }

  private static Ray screenPointToRay(Vector2f point, Vector2f viewportSize, float[] viewProjMtx) {
    if (point == null || viewportSize == null || viewportSize.x <= 0 || viewportSize.y <= 0) {
      return new Ray(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1));
    }
    // Prevent mutating the input point coordinates in-place
    float py = viewportSize.y - point.y;
    float px = point.x;
    float x = px * 2.0F / viewportSize.x - 1.0F;
    float y = py * 2.0F / viewportSize.y - 1.0F;
    float[] farScreenPoint = new float[] {x, y, 1.0F, 1.0F};
    float[] nearScreenPoint = new float[] {x, y, -1.0F, 1.0F};
    float[] nearPlanePoint = new float[4];
    float[] farPlanePoint = new float[4];
    float[] invertedProjectionMatrix = new float[16];
    Matrix.setIdentityM(invertedProjectionMatrix, 0);
    boolean inverted = Matrix.invertM(invertedProjectionMatrix, 0, viewProjMtx, 0);
    if (!inverted) {
      return new Ray(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1));
    }
    Matrix.multiplyMV(nearPlanePoint, 0, invertedProjectionMatrix, 0, nearScreenPoint, 0);
    Matrix.multiplyMV(farPlanePoint, 0, invertedProjectionMatrix, 0, farScreenPoint, 0);

    if (nearPlanePoint[3] == 0 || farPlanePoint[3] == 0) {
      return new Ray(new Vector3f(0, 0, 0), new Vector3f(0, 0, -1));
    }

    Vector3f origin =
        new Vector3f(
            nearPlanePoint[0] / nearPlanePoint[3],
            nearPlanePoint[1] / nearPlanePoint[3],
            nearPlanePoint[2] / nearPlanePoint[3]);
    Vector3f far =
        new Vector3f(
            farPlanePoint[0] / farPlanePoint[3],
            farPlanePoint[1] / farPlanePoint[3],
            farPlanePoint[2] / farPlanePoint[3]);
    Vector3f direction = new Vector3f(far);
    direction.sub(origin);
    if (direction.lengthSquared() > 1e-8f) {
      direction.normalize();
    } else {
      direction = new Vector3f(0, 0, -1);
    }
    return new Ray(origin, direction);
  }

  private static Ray projectRay(
      Vector2f touchPoint,
      float screenWidth,
      float screenHeight,
      float[] projectionMatrix,
      float[] viewMatrix) {
    float[] viewProjMtx = new float[16];
    Matrix.multiplyMM(viewProjMtx, 0, projectionMatrix, 0, viewMatrix, 0);
    return screenPointToRay(touchPoint, new Vector2f(screenWidth, screenHeight), viewProjMtx);
  }

  public static boolean distanceCheck(Vector3f newPoint, Vector3f lastPoint) {
    if (newPoint == null || lastPoint == null) {
      return false;
    }
    Vector3f temp = new Vector3f();
    temp.sub(newPoint, lastPoint);
    return temp.length() > Settings.getMinDistance();
  }
}
