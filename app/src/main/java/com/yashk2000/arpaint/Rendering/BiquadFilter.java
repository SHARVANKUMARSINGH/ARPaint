package com.yashk2000.arpaint.Rendering;

import javax.vecmath.Vector3f;

/** BiquadFilter is an object for easily lowpass filtering incoming values. */
public class BiquadFilter {
  private final BiquadFilterInstance[] inst = new BiquadFilterInstance[3];

  public BiquadFilter(double Fc) {
    for (int i = 0; i < 3; i++) {
      inst[i] = new BiquadFilterInstance(Fc);
    }
  }

  public synchronized Vector3f update(Vector3f in) {
    if (in == null) {
      return new Vector3f(0, 0, 0);
    }
    float x = (float) inst[0].process(in.x);
    float y = (float) inst[1].process(in.y);
    float z = (float) inst[2].process(in.z);
    return new Vector3f(x, y, z);
  }

  public synchronized void reset(Vector3f initialValue) {
    if (initialValue != null) {
      for (int i = 0; i < 3; i++) {
        inst[i].reset(i == 0 ? initialValue.x : (i == 1 ? initialValue.y : initialValue.z));
      }
    }
  }

  private static class BiquadFilterInstance {
    double a0, a1, a2, b1, b2;
    double Fc = 0.5, Q = 0.707;
    double z1 = 0.0, z2 = 0.0;

    BiquadFilterInstance(double fc) {
      Fc = fc;
      calcBiquad();
    }

    void reset(double initial) {
      z1 = initial * (1.0 - a0);
      z2 = initial * (a2 - b2);
    }

    double process(double in) {
      double out = in * a0 + z1;
      z1 = in * a1 + z2 - b1 * out;
      z2 = in * a2 - b2 * out;
      return out;
    }

    void calcBiquad() {
      double K = Math.tan(Math.PI * Fc);
      double norm = 1.0 / (1.0 + K / Q + K * K);
      a0 = K * K * norm;
      a1 = 2.0 * a0;
      a2 = a0;
      b1 = 2.0 * (K * K - 1.0) * norm;
      b2 = (1.0 - K / Q + K * K) * norm;
    }
  }
}
