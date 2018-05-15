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

public class SimulationGpsMeasurement {
  private int svid;
  private double timeOffsetNanos;
  private int state;
  private long receivedSvTimeNanos;
  private long receivedSvTimeUncertaintyNanos;
  private double cn0DbHz;
  private double pseudorangeRateMetersPerSecond;
  private double pseudorangeRateUncertaintyMetersPerSecond;
  private int accumulatedDeltaRangeState;
  private double accumulatedDeltaRangeMeters;
  private double accumulatedDeltaRangeUncertaintyMeters;
  private float carrierFrequencyHz;
  private boolean isHasCarrierFrequencyHz;
  private long carrierCycles;
  private boolean isHasCarrierCycles;
  private double carrierPhase;
  private boolean isHasCarrierPhase;
  private double carrierPhaseUncertainty;
  private boolean isHasCarrierPhaseUncertainty;
  private int multipathIndicator;
  private double snrInDb;
  private boolean isHasSnrInDb;
  private int constellationType;
  private double automaticGainControlLevelDb;
  private boolean isHasAutomaticGainControlLevelDb;

  public void setSvid(int svid) {
    this.svid = svid;
  }
  public int getSvid() {
    return svid;
  }

  public void setTimeOffsetNanos(double timeOffsetNanos) {
    this.timeOffsetNanos = timeOffsetNanos;
  }
  public double getTimeOffsetNanos() {
    return timeOffsetNanos;
  }

  public void setState(int state) {
    this.state = state;
  }
  public int getState() {
    return state;
  }

  public void setReceivedSvTimeNanos(long receivedSvTimeNanos) {
    this.receivedSvTimeNanos = receivedSvTimeNanos;
  }
  public long getReceivedSvTimeNanos() {
    return receivedSvTimeNanos;
  }

  public void setReceivedSvTimeUncertaintyNanos(long receivedSvTimeUncertaintyNanos) {
    this.receivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos;
  }
  public long getReceivedSvTimeUncertaintyNanos() {
    return receivedSvTimeUncertaintyNanos;
  }

  public void setCn0DbHz(double cn0DbHz) {
    this.cn0DbHz = cn0DbHz;
  }
  public double getCn0DbHz() {
    return cn0DbHz;
  }

  public void setPseudorangeRateMetersPerSecond(double pseudorangeRateMetersPerSecond) {
    this.pseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond;
  }
  public double getPseudorangeRateMetersPerSecond() {
    return pseudorangeRateMetersPerSecond;
  }

  public void setPseudorangeRateUncertaintyMetersPerSecond(double pseudorangeRateUncertaintyMetersPerSecond) {
    this.pseudorangeRateUncertaintyMetersPerSecond = pseudorangeRateUncertaintyMetersPerSecond;
  }
  public double getPseudorangeRateUncertaintyMetersPerSecond() {
    return pseudorangeRateUncertaintyMetersPerSecond;
  }

  public void setAccumulatedDeltaRangeState(int accumulatedDeltaRangeState) {
    this.accumulatedDeltaRangeState = accumulatedDeltaRangeState;
  }
  public int getAccumulatedDeltaRangeState() {
    return accumulatedDeltaRangeState;
  }

  public void setAccumulatedDeltaRangeMeters(double accumulatedDeltaRangeMeters) {
    this.accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters;
  }
  public double getAccumulatedDeltaRangeMeters() {
    return accumulatedDeltaRangeMeters;
  }

  public void setAccumulatedDeltaRangeUncertaintyMeters(double accumulatedDeltaRangeUncertaintyMeters) {
    this.accumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters;
  }
  public double getAccumulatedDeltaRangeUncertaintyMeters() {
    return accumulatedDeltaRangeUncertaintyMeters;
  }

  public void setCarrierFrequencyHz(float carrierFrequencyHz) {
    this.carrierFrequencyHz = carrierFrequencyHz;
  }
  public float getCarrierFrequencyHz() {
    return carrierFrequencyHz;
  }
  public void setHasCarrierFrequencyHz(boolean value) {
    isHasCarrierFrequencyHz = value;
  }
  public boolean hasCarrierFrequencyHz() {
    return isHasCarrierFrequencyHz;
  }

  public void setCarrierCycles(long carrierCycles) {
    this.carrierCycles = carrierCycles;
  }
  public long getCarrierCycles() {
    return carrierCycles;
  }
  public void setHasCarrierCycles(boolean value) {
    isHasCarrierCycles = value;
  }
  public boolean hasCarrierCycles() {
    return isHasCarrierCycles;
  }

  public void setCarrierPhase(double carrierPhase) {
    this.carrierPhase = carrierPhase;
  }
  public double getCarrierPhase() {
    return carrierPhase;
  }
  public void setHasCarrierPhase(boolean value) {
    isHasCarrierPhase = value;
  }
  public boolean hasCarrierPhase() {
    return isHasCarrierPhase;
  }

  public void setCarrierPhaseUncertainty(double carrierPhaseUncertainty) {
    this.carrierPhaseUncertainty = carrierPhaseUncertainty;
  }
  public double getCarrierPhaseUncertainty() {
    return carrierPhaseUncertainty;
  }
  public void setHasCarrierPhaseUncertainty(boolean value) {
    isHasCarrierPhaseUncertainty = value;
  }
  public boolean hasCarrierPhaseUncertainty() {
    return isHasCarrierPhaseUncertainty;
  }

  public void setMultipathIndicator(int multipathIndicator) {
    this.multipathIndicator = multipathIndicator;
  }
  public int getMultipathIndicator() {
    return multipathIndicator;
  }

  public void setSnrInDb(double snrInDb) {
    this.snrInDb = snrInDb;
  }
  public double getSnrInDb() {
    return snrInDb;
  }
  public void setHasSnrInDb(boolean value) {
    isHasSnrInDb = value;
  }
  public boolean hasSnrInDb() {
    return isHasSnrInDb;
  }

  public void setConstellationType(int constellationType) {
    this.constellationType = constellationType;
  }
  public int getConstellationType() {
    return constellationType;
  }

  public void setAutomaticGainControlLevelDb(double automaticGainControlLevelDb) {
    this.automaticGainControlLevelDb = automaticGainControlLevelDb;
  }
  public double getAutomaticGainControlLevelDb() {
    return automaticGainControlLevelDb;
  }
  public void setHasAutomaticGainControlLevelDb(boolean value) {
    isHasAutomaticGainControlLevelDb = value;
  }
  public boolean hasAutomaticGainControlLevelDb() {
    return isHasAutomaticGainControlLevelDb;
  }
}