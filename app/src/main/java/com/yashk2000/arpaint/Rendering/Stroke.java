package com.yashk2000.arpaint.Rendering;

import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Vector3f;

/**
 * Represents a single continuous 3D stroke drawn in AR space.
 * Retains its own independent color and thickness so that changes
 * to active settings never mutate or overwrite previously drawn artwork.
 */
public class Stroke {
  private final List<Vector3f> points = new ArrayList<>();
  private Vector3f color;
  private float lineWidth;

  public Stroke(Vector3f color, float lineWidth) {
    this.color = (color != null) ? new Vector3f(color) : new Vector3f(1.0f, 1.0f, 1.0f);
    this.lineWidth = Math.max(0.05f, lineWidth);
  }

  public List<Vector3f> getPoints() {
    return points;
  }

  public void addPoint(Vector3f point) {
    if (point != null) {
      points.add(new Vector3f(point));
    }
  }

  public Vector3f getColor() {
    return color;
  }

  public void setColor(Vector3f color) {
    if (color != null) {
      this.color = new Vector3f(color);
    }
  }

  public float getLineWidth() {
    return lineWidth;
  }

  public void setLineWidth(float lineWidth) {
    this.lineWidth = Math.max(0.05f, lineWidth);
  }

  public int size() {
    return points.size();
  }
}
