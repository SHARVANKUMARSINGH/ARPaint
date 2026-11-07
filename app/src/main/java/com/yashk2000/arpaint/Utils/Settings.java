package com.yashk2000.arpaint.Utils;

import android.graphics.Color;
import javax.vecmath.Vector3f;

public class Settings {
  private static volatile Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
  private static volatile float lineWidth = 0.33f;
  private static volatile boolean shadowsEnabled = true;
  private static volatile boolean physicsEnabled = false;

  private static final float strokeDrawDistance = 0.125f;
  private static final float minDistance = 0.000625f;
  private static final float nearClip = 0.001f;
  private static final float farClip = 100.0f;

  public static Vector3f getColor() {
    return color;
  }

  public static float getLineWidth() {
    return lineWidth;
  }

  public static void setLineWidth(float width) {
    lineWidth = Math.max(0.05f, Math.min(width, 4.0f));
  }

  public static boolean isShadowsEnabled() {
    return shadowsEnabled;
  }

  public static void setShadowsEnabled(boolean enabled) {
    shadowsEnabled = enabled;
  }

  public static boolean isPhysicsEnabled() {
    return physicsEnabled;
  }

  public static void setPhysicsEnabled(boolean enabled) {
    physicsEnabled = enabled;
  }

  public static float getStrokeDrawDistance() {
    return strokeDrawDistance;
  }

  public static float getMinDistance() {
    return minDistance;
  }

  public static float getNearClip() {
    return nearClip;
  }

  public static float getFarClip() {
    return farClip;
  }

  public static void setColor(Vector3f newColor) {
    if (newColor != null) {
      Settings.color = new Vector3f(newColor);
    }
  }

  public static void setColorFromInt(int intColor) {
    float r = Color.red(intColor) / 255.0f;
    float g = Color.green(intColor) / 255.0f;
    float b = Color.blue(intColor) / 255.0f;
    Settings.color = new Vector3f(r, g, b);
  }

  public static int getIntColor() {
    Vector3f c = color;
    int r = Math.min(255, Math.max(0, Math.round(c.x * 255.0f)));
    int g = Math.min(255, Math.max(0, Math.round(c.y * 255.0f)));
    int b = Math.min(255, Math.max(0, Math.round(c.z * 255.0f)));
    return Color.rgb(r, g, b);
  }
}
