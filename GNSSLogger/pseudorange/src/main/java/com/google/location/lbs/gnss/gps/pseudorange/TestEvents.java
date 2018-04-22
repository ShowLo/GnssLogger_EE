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

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.location.cts.suplClient.SuplRrlpController;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for calculating Gps position and velocity solution using weighted least squares
 * where the raw Gps measurements are parsed as a {@link BufferedReader} with the option to apply
 * doppler smoothing, carrier phase smoothing or no smoothing.
 *
 */
public class TestEvents {

  private static final String TAG = "TestEvents";

  private final TestLeastSquare testLeastSquare = new TestLeastSquare();
  // navigation message parser
  private double[] mPseudolitePositioningSolutionXYZ = GpsMathOperations.createAndFillArray(3, Double.NaN);

  // information of pseudolites 伪卫星信息
  private PseudoliteMessageStore mPseudoliteMessageStore = new PseudoliteMessageStore();

  /**
   * Computes Weighted least square position solutions from a received {@link
   * GnssMeasurementsEvent} and store the result in {@link
   * TestEvents#mPseudolitePositioningSolutionXYZ}
   */
  public void computePseudolitePositioningSolutionsFromRawMeas()
      throws Exception {
    // start with last known position and velocity of zero. Following the structure:
    // [X position, Y position, Z position, clock bias]
    double[] positionSolution = GpsMathOperations.createAndFillArray(4, 0);
    performPseudolitePositioningComputation(positionSolution);

    mPseudolitePositioningSolutionXYZ[0] = positionSolution[0];
    mPseudolitePositioningSolutionXYZ[1] = positionSolution[1];
    mPseudolitePositioningSolutionXYZ[2] = positionSolution[2];

    Log.d(
        TAG,
        "X, Y, Z: "
            + mPseudolitePositioningSolutionXYZ[0]
            + " "
            + mPseudolitePositioningSolutionXYZ[1]
            + " "
            + mPseudolitePositioningSolutionXYZ[2]);
  }


  /**
   * Calculates least square pseudolite positioning solutions from an array of {@link GpsMeasurement}
   * in meters and store the result in {@code positionSolution}
   */
  private void performPseudolitePositioningComputation(double[] positionSolution)
      throws Exception {

    // calculate iterative least square position solution and velocity solutions
    testLeastSquare.calculatePseudolitePositioningLeastSquare(mPseudoliteMessageStore, positionSolution);

    Log.d(
        TAG,
        "Least Square Pseudolite Positioning Solution in meters: "
            + positionSolution[0]
            + " "
            + positionSolution[1]
            + " "
            + positionSolution[2]);
    Log.d(TAG, "Estimated Receiver clock offset in meters: " + positionSolution[3]);
  }


  /** Returns the last computed weighted least square position solution */
  public double[] getPseudolitePositioningSolutionXYZ() {
    return mPseudolitePositioningSolutionXYZ;
  }
}
