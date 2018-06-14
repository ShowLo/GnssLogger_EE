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

import java.util.Collection;

public class SimulationGpsMeasurementsEvent {
  private SimulationGpsClock simulationGpsClock;
  private Collection<SimulationGpsMeasurement> simulationGpsMeasurements;

  public void setSimulationGpsClock(SimulationGpsClock simulationGpsClock) {
    this.simulationGpsClock = simulationGpsClock;
  }
  public SimulationGpsClock getClock() {
    return simulationGpsClock;
  }

  public void setSimulationGpsMeasurements(Collection<SimulationGpsMeasurement> simulationGpsMeasurements) {
    this.simulationGpsMeasurements = simulationGpsMeasurements;
  }
  public Collection<SimulationGpsMeasurement> getMeasurements() {
    return simulationGpsMeasurements;
  }
}