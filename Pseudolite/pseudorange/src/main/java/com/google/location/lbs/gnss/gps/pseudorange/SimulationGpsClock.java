/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * modified by Chen Jiarong, Department of Electronic Engineering, Tsinghua University
 */

package com.google.location.lbs.gnss.gps.pseudorange;

public class SimulationGpsClock {
  private long timeNanos;
  private int leapSecond;
  private boolean isHasLeapSecond;
  private double timeUncertaintyNanos;
  private boolean isHasTimeUncertaintyNanos;
  private long fullBiasNanos;
  private boolean isHasFullBiasNanos;
  private double biasNanos;
  private boolean isHasBiasNanos;
  private double biasUncertaintyNanos;
  private boolean isHasBiasUncertaintyNanos;
  private double driftNanosPerSecond;
  private boolean isHasDriftNanosPerSecond;
  private double driftUncertaintyNanosPerSecond;
  private boolean isHasDriftUncertaintyNanosPerSecond;
  private int hardwareClockDiscontinuityCount;

  public void setTimeNanos(long timeNanos) {
    this.timeNanos = timeNanos;
  }
  public long getTimeNanos() {
    return timeNanos;
  }

  public void setLeapSecond(int leapSecond) {
    this.leapSecond = leapSecond;
  }
  public int getLeapSecond() {
    return leapSecond;
  }
  public void setHasLeapSecond(boolean value) {
    isHasLeapSecond = value;
  }
  public boolean hasLeapSecond() {
    return isHasLeapSecond;
  }

  public void setTimeUncertaintyNanos(double timeUncertaintyNanos) {
    this.timeUncertaintyNanos = timeUncertaintyNanos;
  }
  public double getTimeUncertaintyNanos() {
    return timeUncertaintyNanos;
  }
  public void setHasTimeUncertaintyNanos(boolean value) {
    isHasTimeUncertaintyNanos = value;
  }
  public boolean hasTimeUncertaintyNanos() {
    return isHasTimeUncertaintyNanos;
  }

  public void setFullBiasNanos(long fullBiasNanos) {
    this.fullBiasNanos = fullBiasNanos;
  }
  public long getFullBiasNanos() {
    return fullBiasNanos;
  }
  public void setHasFullBiasNanos(boolean hasFullBiasNanos) {
    isHasFullBiasNanos = hasFullBiasNanos;
  }
  public boolean hasFullBiasNanos() {
    return isHasFullBiasNanos;
  }

  public void setBiasNanos(double biasNanos) {
    this.biasNanos = biasNanos;
  }
  public double getBiasNanos() {
    return biasNanos;
  }
  public void setHasBiasNanos(boolean value) {
    isHasBiasNanos = value;
  }
  public boolean hasBiasNanos() {
    return isHasBiasNanos;
  }

  public void setBiasUncertaintyNanos(double biasUncertaintyNanos) {
    this.biasUncertaintyNanos = biasUncertaintyNanos;
  }
  public double getBiasUncertaintyNanos() {
    return biasUncertaintyNanos;
  }
  public void setHasBiasUncertaintyNanos(boolean value) {
    isHasBiasUncertaintyNanos = value;
  }
  public boolean hasBiasUncertaintyNanos() {
    return isHasBiasUncertaintyNanos;
  }

  public void setDriftNanosPerSecond(double driftNanosPerSecond) {
    this.driftNanosPerSecond = driftNanosPerSecond;
  }
  public double getDriftNanosPerSecond() {
    return driftNanosPerSecond;
  }
  public void setHasDriftNanosPerSecond(boolean value) {
    isHasDriftNanosPerSecond = value;
  }
  public boolean hasDriftNanosPerSecond() {
    return isHasDriftNanosPerSecond;
  }

  public void setDriftUncertaintyNanosPerSecond(double driftUncertaintyNanosPerSecond) {
    this.driftUncertaintyNanosPerSecond = driftUncertaintyNanosPerSecond;
  }
  public double getDriftUncertaintyNanosPerSecond() {
    return driftUncertaintyNanosPerSecond;
  }
  public void setHasDriftUncertaintyNanosPerSecond(boolean value) {
    isHasDriftUncertaintyNanosPerSecond = value;
  }
  public boolean hasDriftUncertaintyNanosPerSecond() {
    return isHasDriftUncertaintyNanosPerSecond;
  }

  public void setHardwareClockDiscontinuityCount(int hardwareClockDiscontinuityCount) {
    this.hardwareClockDiscontinuityCount = hardwareClockDiscontinuityCount;
  }
  public int getHardwareClockDiscontinuityCount() {
    return hardwareClockDiscontinuityCount;
  }
}