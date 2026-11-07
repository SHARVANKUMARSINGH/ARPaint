package com.yashk2000.arpaint.Rendering;

import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;

/**
 * Represents a single continuous 3D stroke drawn in AR space.
 * Retains its own independent color and thickness.
 * Supports real-time rigid-body AR physics with gravity, velocity, bounce, and rotational torque.
 */
public class Stroke {
  private final List<Vector3f> points = new ArrayList<>();
  private final List<Vector3f> originalPoints = new ArrayList<>();
  private final List<Vector3f> relativeOffsets = new ArrayList<>();

  private Vector3f color;
  private float lineWidth;

  // Physics state
  private boolean physicsInitialized = false;
  private boolean isSettled = false;
  private final Vector3f centerOfMass = new Vector3f(0f, 0f, 0f);
  private final Vector3f velocity = new Vector3f(0f, 0f, 0f);
  private final Vector3f angularVelocity = new Vector3f(0f, 0f, 0f);
  private final Matrix3f rotationMatrix = new Matrix3f();

  public Stroke(Vector3f color, float lineWidth) {
    this.color = (color != null) ? new Vector3f(color) : new Vector3f(1.0f, 1.0f, 1.0f);
    this.lineWidth = Math.max(0.05f, lineWidth);
    this.rotationMatrix.setIdentity();
  }

  public List<Vector3f> getPoints() {
    return points;
  }

  public void addPoint(Vector3f point) {
    if (point != null) {
      points.add(new Vector3f(point));
      originalPoints.add(new Vector3f(point));
      physicsInitialized = false; // Need re-init if points changed
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

  public boolean isPhysicsInitialized() {
    return physicsInitialized;
  }

  public void initializePhysics() {
    if (points.size() < 2) return;

    centerOfMass.set(0f, 0f, 0f);
    for (Vector3f p : points) {
      centerOfMass.add(p);
    }
    centerOfMass.scale(1.0f / points.size());

    relativeOffsets.clear();
    for (Vector3f p : points) {
      Vector3f offset = new Vector3f(p);
      offset.sub(centerOfMass);
      relativeOffsets.add(offset);
    }

    velocity.set(0f, 0f, 0f);
    angularVelocity.set(0f, 0f, 0f);
    rotationMatrix.setIdentity();
    isSettled = false;
    physicsInitialized = true;
  }

  public void resetPhysics() {
    if (originalPoints.size() == points.size()) {
      for (int i = 0; i < points.size(); i++) {
        points.get(i).set(originalPoints.get(i));
      }
    }
    velocity.set(0f, 0f, 0f);
    angularVelocity.set(0f, 0f, 0f);
    rotationMatrix.setIdentity();
    isSettled = false;
    physicsInitialized = false;
  }

  /**
   * Advances physics simulation for this stroke by dt seconds.
   * @param floorY height of the detected floor in AR coordinates.
   * @param dt delta time in seconds.
   * @return true if stroke moved, false if sleeping/idle.
   */
  public boolean stepPhysics(float floorY, float dt) {
    if (!physicsInitialized) {
      initializePhysics();
      if (!physicsInitialized) return false;
    }

    if (isSettled) {
      return false;
    }

    // 1. Gravity acceleration
    velocity.y += -2.8f * dt;

    // 2. Air resistance damping
    velocity.scale(0.985f);
    angularVelocity.scale(0.960f);

    // 3. Center of mass position update
    Vector3f stepDisplacement = new Vector3f(velocity);
    stepDisplacement.scale(dt);
    centerOfMass.add(stepDisplacement);

    // 4. Angular rotation update
    float angle = angularVelocity.length() * dt;
    if (angle > 1e-5f) {
      Vector3f axis = new Vector3f(angularVelocity);
      axis.normalize();
      Matrix3f deltaRot = new Matrix3f();
      setAxisAngle(deltaRot, axis, angle);
      rotationMatrix.mul(deltaRot, rotationMatrix);
    }

    // 5. Compute new point coordinates & check ground collision
    boolean hitFloor = false;
    float maxPenetration = 0f;
    Vector3f deepestContactOffset = null;

    for (int i = 0; i < points.size(); i++) {
      Vector3f offset = relativeOffsets.get(i);
      Vector3f rotated = new Vector3f();
      rotationMatrix.transform(offset, rotated);

      Vector3f pt = points.get(i);
      pt.add(centerOfMass, rotated);

      float strokeRadius = 0.012f * lineWidth;
      float penetration = (floorY + strokeRadius) - pt.y;
      if (penetration > 0f) {
        hitFloor = true;
        if (penetration > maxPenetration) {
          maxPenetration = penetration;
          deepestContactOffset = rotated;
        }
      }
    }

    // 6. Floor collision & bounce response
    if (hitFloor && deepestContactOffset != null) {
      // Push center of mass out of floor
      centerOfMass.y += maxPenetration;

      // Bounce restitution
      if (velocity.y < 0f) {
        velocity.y = -velocity.y * 0.48f;
        // Ground friction
        velocity.x *= 0.85f;
        velocity.z *= 0.85f;
      }

      // Rotational torque impulse from contact point
      Vector3f torque = new Vector3f();
      Vector3f upwardImpulse = new Vector3f(0f, maxPenetration * 12.0f, 0f);
      torque.cross(deepestContactOffset, upwardImpulse);
      torque.scale(0.04f);
      angularVelocity.add(torque);

      // Recompute points after penetration resolution
      for (int i = 0; i < points.size(); i++) {
        Vector3f offset = relativeOffsets.get(i);
        Vector3f rotated = new Vector3f();
        rotationMatrix.transform(offset, rotated);
        points.get(i).add(centerOfMass, rotated);
      }

      // Check if stroke has settled to sleep on floor
      if (Math.abs(velocity.y) < 0.05f && velocity.length() < 0.08f && angularVelocity.length() < 0.15f) {
        velocity.set(0f, 0f, 0f);
        angularVelocity.set(0f, 0f, 0f);
        isSettled = true;
      }
    }

    return true; // Stroke moved
  }

  private void setAxisAngle(Matrix3f m, Vector3f axis, float angle) {
    float c = (float) Math.cos(angle);
    float s = (float) Math.sin(angle);
    float t = 1.0f - c;
    float x = axis.x;
    float y = axis.y;
    float z = axis.z;

    m.m00 = c + x * x * t;
    m.m11 = c + y * y * t;
    m.m22 = c + z * z * t;

    float tmp1 = x * y * t;
    float tmp2 = z * s;
    m.m10 = tmp1 + tmp2;
    m.m01 = tmp1 - tmp2;

    tmp1 = x * z * t;
    tmp2 = y * s;
    m.m20 = tmp1 - tmp2;
    m.m02 = tmp1 + tmp2;

    tmp1 = y * z * t;
    tmp2 = x * s;
    m.m21 = tmp1 + tmp2;
    m.m12 = tmp1 - tmp2;
  }
}
