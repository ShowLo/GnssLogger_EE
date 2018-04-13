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
public class PseudolitePositioningFromRealTimeEvents {

  private static final String TAG = "PseudolitePositioningFromRealTimeEvents";
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private static final int TOW_DECODED_MEASUREMENT_STATE_BIT = 3;
  /** Average signal travel time from GPS satellite and earth */
  private static final int VALID_ACCUMULATED_DELTA_RANGE_STATE = 1;
  private static final int MINIMUM_NUMBER_OF_USEFUL_SATELLITES = 4;
  private static final int C_TO_N0_THRESHOLD_DB_HZ = 18;

  private GpsNavMessageProto mHardwareGpsNavMessageProto = null;

  // navigation message parser
  private GpsNavigationMessageStore mGpsNavigationMessageStore = new GpsNavigationMessageStore();
  private double[] mPseudolitePositioningSolutionXYZ = GpsMathOperations.createAndFillArray(3, Double.NaN);
  private boolean mFirstUsefulMeasurementSet = true;
  private int[] mReferenceLocation = null;
  private long mLastReceivedSuplMessageTimeMillis = 0;
  private long mDeltaTimeMillisToMakeSuplRequest = TimeUnit.MINUTES.toMillis(30);
  private boolean mFirstSuplRequestNeeded = true;
  private GpsNavMessageProto mGpsNavMessageProtoUsed = null;

  // information of pseudolites 伪卫星信息
  private PseudoliteMessageStore mPseudoliteMessageStore = new PseudoliteMessageStore();

  // API KEY
  private String elevationApiKey = "AIzaSyC3KoyXGV0yxGKEvT-WU1ioz64wzlXoUDY";

  // Only the interface of pseudorange smoother is provided. Please implement customized smoother.
  PseudorangeSmoother mPseudorangeSmoother = new PseudorangeNoSmoothingSmoother();
  private final PseudolitePositioningLeastSquare mPseudolitePositioningLeastSquareCalculator =
      new PseudolitePositioningLeastSquare(mPseudorangeSmoother, elevationApiKey);
  private GpsMeasurement[] mUsefulSatellitesToReceiverMeasurements =
      new GpsMeasurement[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  private Long[] mUsefulSatellitesToTowNs =
      new Long[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  private long mLargestTowNs = Long.MIN_VALUE;
  private double mArrivalTimeSinceGPSWeekNs = 0.0;
  private int mDayOfYear1To366 = 0;
  private int mGpsWeekNumber = 0;
  private long mArrivalTimeSinceGpsEpochNs = 0;


  private static final String SUPL_SERVER_NAME = "supl.google.com";
  private static final int SUPL_SERVER_PORT = 7276;

  /**
   * Computes Weighted least square position solutions from a received {@link
   * GnssMeasurementsEvent} and store the result in {@link
   * PseudolitePositioningFromRealTimeEvents#mPseudolitePositioningSolutionXYZ}
   */
  public void computePseudolitePositioningSolutionsFromRawMeas(GnssMeasurementsEvent event)
      throws Exception {

    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      mUsefulSatellitesToReceiverMeasurements[i] = null;
      mUsefulSatellitesToTowNs[i] = null;
    }

    GnssClock gnssClock = event.getClock();
    // 这里得到的是当前时间相对于1980.1.6零点的时间，也就是GPS时间
    mArrivalTimeSinceGpsEpochNs = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();
    double biasNanos = gnssClock.getBiasNanos();

    Map<Integer, Double> map = new HashMap<>();

    for (GnssMeasurement measurement : event.getMeasurements()) {
      // ignore any measurement if it is not from GPS constellation
      if (measurement.getConstellationType() != GnssStatus.CONSTELLATION_GPS) {
        continue;
      }
      // ignore raw data if time is zero, if signal to noise ratio is below thresholmd or if
      // TOW is not yet decoded
      if (measurement.getCn0DbHz() >= C_TO_N0_THRESHOLD_DB_HZ
          && (measurement.getState() & (1L << TOW_DECODED_MEASUREMENT_STATE_BIT)) != 0) {

        // calculate day of year and Gps week number needed for the least square
        GpsTime gpsTime = new GpsTime(mArrivalTimeSinceGpsEpochNs);
        // Gps weekly epoch in Nanoseconds: defined as of every Sunday night at 00:00:000
        long gpsWeekEpochNs = GpsTime.getGpsWeekEpochNano(gpsTime);
        // 接收时间(本周中的第几纳秒)
        mArrivalTimeSinceGPSWeekNs = mArrivalTimeSinceGpsEpochNs - gpsWeekEpochNs;
        // 前面有多少周了(相对于1980.1.6)
        mGpsWeekNumber = gpsTime.getGpsWeekSecond().first;
        // calculate day of the year between 1 and 366
        Calendar cal = gpsTime.getTimeInCalendar();
        // 一年中的第几天
        mDayOfYear1To366 = cal.get(Calendar.DAY_OF_YEAR);

        long receivedGPSTowNs = measurement.getReceivedSvTimeNanos();
        if (receivedGPSTowNs > mLargestTowNs) {
          mLargestTowNs = receivedGPSTowNs;
        }
        mUsefulSatellitesToTowNs[measurement.getSvid() - 1] = receivedGPSTowNs;
        GpsMeasurement gpsReceiverMeasurement =
            new GpsMeasurement(
                (long) mArrivalTimeSinceGPSWeekNs,
                measurement.getAccumulatedDeltaRangeMeters(),
                measurement.getAccumulatedDeltaRangeState() == VALID_ACCUMULATED_DELTA_RANGE_STATE,
                measurement.getPseudorangeRateMetersPerSecond(),
                measurement.getCn0DbHz(),
                measurement.getAccumulatedDeltaRangeUncertaintyMeters(),
                measurement.getPseudorangeRateUncertaintyMetersPerSecond(),
                measurement.getTimeOffsetNanos());
        mUsefulSatellitesToReceiverMeasurements[measurement.getSvid() - 1] = gpsReceiverMeasurement;
        map.put(measurement.getSvid() - 1, measurement.getCn0DbHz());
        Log.d("Pseudolite Positioning","The measurement of satellite " + measurement.getSvid() +
            " was added to gpsReceiverMeasurement");
      }
    }

    // A few of satellites with the highest SNR is kept,
    // the number of which is the same as the number of pseudolites
    // 保留信噪比最高的几个卫星，其数量跟伪卫星数量一样
    int pseudoliteNum = mPseudoliteMessageStore.getIndoorAntennasXyz().length;
    List<Map.Entry<Integer, Double>> list = new ArrayList<Map.Entry<Integer, Double>>(map.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
      @Override
      public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
        return o2.getValue().compareTo(o1.getValue());
      }
    });
    int count = 0;
    for (Map.Entry<Integer, Double> item : list) {
      ++count;
      // 去掉可能收到的不是伪卫星发出的信号
      if (count > pseudoliteNum) {
        mUsefulSatellitesToReceiverMeasurements[item.getKey()] = null;
        mUsefulSatellitesToTowNs[item.getKey()] = null;
      }
    }
    // 没有收到所有伪卫星的数据，停止计算
    if (count < pseudoliteNum) {
      Log.d(TAG, "Do not receive all the measurements from pseudolites, stop calculating.");
      return;
    }

    // check if we should continue using the navigation message from the SUPL server, or use the
    // navigation message from the device if we fully received it
    boolean useNavMessageFromSupl =
        continueUsingNavMessageFromSupl(
            mUsefulSatellitesToReceiverMeasurements, mHardwareGpsNavMessageProto);
    if (useNavMessageFromSupl) {
      Log.d(TAG, "Using navigation message from SUPL server");

      if (mFirstSuplRequestNeeded
          || (System.currentTimeMillis() - mLastReceivedSuplMessageTimeMillis)
          > mDeltaTimeMillisToMakeSuplRequest) {
        // The following line is blocking call for SUPL connection and back. But it is fast enough
        mGpsNavMessageProtoUsed = getSuplNavMessage(mReferenceLocation[0], mReferenceLocation[1]);
        if (!isEmptyNavMessage(mGpsNavMessageProtoUsed)) {
          mFirstSuplRequestNeeded = false;
          mLastReceivedSuplMessageTimeMillis = System.currentTimeMillis();
        } else {
          return;
        }
      }
    } else {
      Log.d(TAG, "Using navigation message from the GPS receiver");
      mGpsNavMessageProtoUsed = mHardwareGpsNavMessageProto;
    }

    // 仍然有可见卫星没有对应的星历，停止计算
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (mUsefulSatellitesToReceiverMeasurements[i] != null
          && !navMessageProtoContainsSvid(mGpsNavMessageProtoUsed, i + 1)) {
        Log.d(TAG, "There are visible satellites without useful ephemeris, stop calculating");
        return;
      }
    }

    if (!mFirstUsefulMeasurementSet) {
      // start with last known position and velocity of zero. Following the structure:
      // [X position, Y position, Z position, clock bias]
      double[] positionSolution = GpsMathOperations.createAndFillArray(4, 0);
      performPseudolitePositioningComputation(
              mPseudoliteMessageStore,
              mPseudolitePositioningLeastSquareCalculator,
              mUsefulSatellitesToReceiverMeasurements,
              mUsefulSatellitesToTowNs,
              biasNanos,
              mArrivalTimeSinceGPSWeekNs,
              mDayOfYear1To366,
              mGpsWeekNumber,
              positionSolution);

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
    mFirstUsefulMeasurementSet = false;
  }

  private boolean isEmptyNavMessage(GpsNavMessageProto navMessageProto) {
    if(navMessageProto.iono == null)return true;
    if(navMessageProto.ephemerids.length ==0)return true;
    return  false;
  }

  private boolean navMessageProtoContainsSvid(GpsNavMessageProto navMessageProto, int svid) {
    List<GpsEphemerisProto> ephemeridesList =
            new ArrayList<GpsEphemerisProto>(Arrays.asList(navMessageProto.ephemerids));
    for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
      if (ephProtoFromList.prn == svid) {
        return true;
      }
    }
    return false;
  }

  /**
   * Calculates least square pseudolite positioning solutions from an array of {@link GpsMeasurement}
   * in meters and store the result in {@code positionSolution}
   */
  private void performPseudolitePositioningComputation(
      PseudoliteMessageStore pseudoliteMessageStore,
      PseudolitePositioningLeastSquare pseudolitePositioningLeastSquare,
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      double biasNanos,
      double arrivalTimeSinceGPSWeekNs,
      int dayOfYear1To366,
      int gpsWeekNumber,
      double[] positionSolution)
      throws Exception {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        PseudolitePositioningLeastSquare.computePseudorangeAndUncertainties(
            Arrays.asList(usefulSatellitesToReceiverMeasurements),
            usefulSatellitesToTOWNs,
            biasNanos);

    // calculate iterative least square position solution and velocity solutions
    pseudolitePositioningLeastSquare.calculatePseudolitePositioningLeastSquare(
        pseudoliteMessageStore,
        mGpsNavMessageProtoUsed,
        usefulSatellitesToPseudorangeMeasurements,
        arrivalTimeSinceGPSWeekNs * SECONDS_PER_NANO,
        gpsWeekNumber,
        dayOfYear1To366,
        positionSolution);

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

  /**
   * Checks if we should continue using the navigation message from the SUPL server, or use the
   * navigation message from the device if we fully received it. If the navigation message read from
   * the receiver has all the visible satellite ephemerides, return false, otherwise, return true.
   */
  private static boolean continueUsingNavMessageFromSupl(
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      GpsNavMessageProto hardwareGpsNavMessageProto) {
    boolean useNavMessageFromSupl = true;
    if (hardwareGpsNavMessageProto != null) {
      ArrayList<GpsEphemerisProto> hardwareEphemeridesList=
          new ArrayList<GpsEphemerisProto>(Arrays.asList(hardwareGpsNavMessageProto.ephemerids));
      if (hardwareGpsNavMessageProto.iono != null) {
        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
          if (usefulSatellitesToReceiverMeasurements[i] != null) {
            int prn = i + 1;
            for (GpsEphemerisProto hardwareEphProtoFromList : hardwareEphemeridesList) {
              if (hardwareEphProtoFromList.prn == prn) {
                useNavMessageFromSupl = false;
                break;
              }
              useNavMessageFromSupl = true;
            }
            if (useNavMessageFromSupl == true) {
              break;
            }
          }
        }
      }
    }
    return useNavMessageFromSupl;
  }

  /**
   * Parses a string array containing an updates to the navigation message and return the most
   * recent {@link GpsNavMessageProto}.
   */
  public void parseHwNavigationMessageUpdates(GnssNavigationMessage navigationMessage) {
    byte messagePrn = (byte) navigationMessage.getSvid();
    byte messageType = (byte) (navigationMessage.getType() >> 8);
    int subMessageId = navigationMessage.getSubmessageId();

    byte[] messageRawData = navigationMessage.getData();

    // parse only GPS navigation messages for now
    if (messageType == 1) {
      mGpsNavigationMessageStore.onNavMessageReported(
          messagePrn, messageType, (short) subMessageId, messageRawData);
      mHardwareGpsNavMessageProto = mGpsNavigationMessageStore.createDecodedNavMessage();
    }

  }

  /** Sets a rough location of the receiver that can be used to request SUPL assistance data */
  public void setReferencePosition(int latE7, int lngE7, int altE7) {
    if (mReferenceLocation == null) {
      mReferenceLocation = new int[3];
    }
    mReferenceLocation[0] = latE7;
    mReferenceLocation[1] = lngE7;
    mReferenceLocation[2] = altE7;
  }

  /**
   * Reads the navigation message from the SUPL server by creating a Stubby client to Stubby server
   * that wraps the SUPL server. The input is the time in nanoseconds since the GPS epoch at which
   * the navigation message is required and the output is a {@link GpsNavMessageProto}
   *
   * @throws IOException
   * @throws UnknownHostException
   */
  private GpsNavMessageProto getSuplNavMessage(long latE7, long lngE7)
      throws UnknownHostException, IOException {
    SuplRrlpController suplRrlpController =
        new SuplRrlpController(SUPL_SERVER_NAME, SUPL_SERVER_PORT);
    GpsNavMessageProto navMessageProto = suplRrlpController.generateNavMessage(latE7, lngE7);

    return navMessageProto;
  }

  /** Returns the last computed weighted least square position solution */
  public double[] getPseudolitePositioningSolutionXYZ() {
    return mPseudolitePositioningSolutionXYZ;
  }
}
