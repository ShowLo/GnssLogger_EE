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

import com.google.location.lbs.gnss.gps.pseudorange.Ecef2LlaConverter.GeodeticLlaValues;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Helper class for calculating Gps position and velocity solution using weighted least squares
 * where the raw Gps measurements are parsed as a {@link BufferedReader} with the option to apply
 * doppler smoothing, carrier phase smoothing or no smoothing.
 *
 */
public class PseudorangePositionFromRealTimeEvents {

  private static final String TAG = "PseudorangePositionFromRealTimeEvents";
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private static final int TOW_DECODED_MEASUREMENT_STATE_BIT = 3;
  /** Average signal travel time from GPS satellite and earth */
  private static final int VALID_ACCUMULATED_DELTA_RANGE_STATE = 1;
  private static final int MINIMUM_NUMBER_OF_USEFUL_SATELLITES = 4;
  private static final int C_TO_N0_THRESHOLD_DB_HZ = 18;

  private GpsNavMessageProto mHardwareGpsNavMessageProto = null;

  // navigation message parser
  private GpsNavigationMessageStore mGpsNavigationMessageStore = new GpsNavigationMessageStore();
  private double[] mPositionSolutionLatLngDeg = GpsMathOperations.createAndFillArray(3, Double.NaN);
  private boolean mFirstUsefulMeasurementSet = true;
  // private int[] mReferenceLocation = null;
  private GpsNavMessageProto mGpsNavMessageProtoUsed = null;

  private String elevationApiKey = "AIzaSyC3KoyXGV0yxGKEvT-WU1ioz64wzlXoUDY";

  // Only the interface of pseudorange smoother is provided. Please implement customized smoother.
  PseudorangeSmoother mPseudorangeSmoother = new PseudorangeNoSmoothingSmoother();
  private final UserPositionWeightedLeastSquare mUserPositionLeastSquareCalculator =
      new UserPositionWeightedLeastSquare(mPseudorangeSmoother, elevationApiKey);
  private GpsMeasurement[] mUsefulSatellitesToReceiverMeasurements =
      new GpsMeasurement[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  private Long[] mUsefulSatellitesToTowNs =
      new Long[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  // private long mLargestTowNs = Long.MIN_VALUE;
  private double biasNanos = 0.0;
  private double mArrivalTimeSinceGPSWeekNs = 0.0;
  private int mDayOfYear1To366 = 0;
  private int mGpsWeekNumber = 0;
  private long mArrivalTimeSinceGpsEpochNs = 0;

  private int[] mReferenceLocation = null;

  private static final String SUPL_SERVER_NAME = "supl.google.com";
  private static final int SUPL_SERVER_PORT = 7276;

  private double[] mPseudorangesMeters =
      GpsMathOperations.createAndFillArray(
          GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES, Double.NaN
      );

  /**
   * Computes Weighted least square position solutions from a received {@link
   * GnssMeasurementsEvent} and store the result in {@link
   * PseudorangePositionFromRealTimeEvents#mPositionSolutionLatLngDeg}
   */
  public void computePositionSolutionsFromRawMeas(GnssMeasurementsEvent event)
      throws Exception {
    mPseudorangesMeters =
        GpsMathOperations.createAndFillArray(
            GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES, Double.NaN
        );

    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      mUsefulSatellitesToReceiverMeasurements[i] = null;
      mUsefulSatellitesToTowNs[i] = null;
    }

    GnssClock gnssClock = event.getClock();
    // 这里得到的是当前时间相对于1980.1.6零点的时间
    mArrivalTimeSinceGpsEpochNs = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();
    biasNanos = gnssClock.getBiasNanos();

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
        /*if (receivedGPSTowNs > mLargestTowNs) {
          mLargestTowNs = receivedGPSTowNs;
        }*/
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
        Log.d("测试解调",measurement.getSvid()+"卫星的measurement加入");
      }
    }

    // check if we can use the navigation message from the device
    boolean canUse =
        canUsingNavMessageFromDevice(
            mUsefulSatellitesToReceiverMeasurements, mHardwareGpsNavMessageProto);
    if (canUse) {
      Log.d(TAG, "Using navigation message from the GPS receiver");
      mGpsNavMessageProtoUsed = mHardwareGpsNavMessageProto;
    } else {
      Log.d(TAG, "Received less than four visible satellites' ephemerides ..."
                      + "no position is calculated using navigation message.");
      Log.d(TAG, "So we have to use navigation message from SUPL server");
      if (mReferenceLocation == null) {
        Log.d(TAG, "The reference location is null, so we cannot get navigaion"
                      + "message from SUPL server.");
        mPositionSolutionLatLngDeg = GpsMathOperations.createAndFillArray(3, Double.NaN);
        return;
      } else {
        mGpsNavMessageProtoUsed = getSuplNavMessage(mReferenceLocation[0], mReferenceLocation[1]);
        Log.d(TAG, "Now we have got navigation message from SUPL server.");
      }
    }

    // remove those visible satellites without useful ephemeris
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (mUsefulSatellitesToReceiverMeasurements[i] != null
          && !navMessageProtoContainsSvid(mGpsNavMessageProtoUsed, i + 1)) {
        mUsefulSatellitesToReceiverMeasurements[i] = null;
        mUsefulSatellitesToTowNs[i] = null;
      }
    }

    // calculate the number of useful satellites
    int numberOfUsefulSatellites = 0;
    for (GpsMeasurement element : mUsefulSatellitesToReceiverMeasurements) {
      if (element != null) {
        numberOfUsefulSatellites++;
      }
    }

    if (numberOfUsefulSatellites < MINIMUM_NUMBER_OF_USEFUL_SATELLITES) {
      Log.d(TAG, "Less than 4 useful measurements, so no position calculated.");
      mPositionSolutionLatLngDeg = GpsMathOperations.createAndFillArray(3, Double.NaN);
      return;
    }

    if (!mFirstUsefulMeasurementSet) {
      // start with last known position of zero. Following the structure:
      // [X position, Y position, Z position, clock bias]
      double[] positionSolutionEcef = GpsMathOperations.createAndFillArray(4, 0);
      performPositionComputationEcef(
              mUserPositionLeastSquareCalculator,
              mUsefulSatellitesToReceiverMeasurements,
              mUsefulSatellitesToTowNs,
              biasNanos,
              mArrivalTimeSinceGPSWeekNs,
              mDayOfYear1To366,
              mGpsWeekNumber,
              positionSolutionEcef);
      // convert the position solution from ECEF to latitude, longitude and altitude
      GeodeticLlaValues latLngAlt =
              Ecef2LlaConverter.convertECEFToLLACloseForm(
                      positionSolutionEcef[0],
                      positionSolutionEcef[1],
                      positionSolutionEcef[2]);
      mPositionSolutionLatLngDeg[0] = Math.toDegrees(latLngAlt.latitudeRadians);
      mPositionSolutionLatLngDeg[1] = Math.toDegrees(latLngAlt.longitudeRadians);
      mPositionSolutionLatLngDeg[2] = latLngAlt.altitudeMeters;

      Log.d(
              TAG,
              "Latitude, Longitude, Altitude: "
                      + mPositionSolutionLatLngDeg[0]
                      + " "
                      + mPositionSolutionLatLngDeg[1]
                      + " "
                      + mPositionSolutionLatLngDeg[2]);
    }
    mFirstUsefulMeasurementSet = false;
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
   * Calculates ECEF least square position solutions from an array of {@link GpsMeasurement}
   * in meters and store the result in {@code positionVelocitySolutionEcef}
   */
  private void performPositionComputationEcef(
      UserPositionWeightedLeastSquare userPositionLeastSquare,
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      double biasNanos,
      double arrivalTimeSinceGPSWeekNs,
      int dayOfYear1To366,
      int gpsWeekNumber,
      double[] positionSolutionEcef)
      throws Exception {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        UserPositionWeightedLeastSquare.computePseudorangeAndUncertainties(
            Arrays.asList(usefulSatellitesToReceiverMeasurements),
            usefulSatellitesToTOWNs,
            biasNanos);

    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; ++i) {
      if (usefulSatellitesToPseudorangeMeasurements.get(i) != null) {
        mPseudorangesMeters[i] = usefulSatellitesToPseudorangeMeasurements.get(i).pseudorangeMeters;
      } else {
        mPseudorangesMeters[i] = Double.NaN;
      }
    }

    // calculate iterative least square position solution
    userPositionLeastSquare.calculateUserPositionLeastSquare(
        mGpsNavMessageProtoUsed,
        usefulSatellitesToPseudorangeMeasurements,
        arrivalTimeSinceGPSWeekNs * SECONDS_PER_NANO,
        gpsWeekNumber,
        dayOfYear1To366,
        positionSolutionEcef);

    Log.d(
        TAG,
        "Least Square Position Solution in ECEF meters: "
            + positionSolutionEcef[0]
            + " "
            + positionSolutionEcef[1]
            + " "
            + positionSolutionEcef[2]);
    Log.d(TAG, "Estimated Receiver clock offset in meters: " + positionSolutionEcef[3]);
  }

  /**
   * Checks if we can use the navigation message from the device. If we fully received less
   * than 4 visible satellite ephemerides, return false, otherwise, return true.
   */
  private static boolean canUsingNavMessageFromDevice(
          GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
          GpsNavMessageProto hardwareGpsNavMessageProto) {
    int count = 0;
    if (hardwareGpsNavMessageProto != null) {
      ArrayList<GpsEphemerisProto> hardwareEphemeridesList=
              new ArrayList<GpsEphemerisProto>(Arrays.asList(hardwareGpsNavMessageProto.ephemerids));
      Log.d("测试解调","有hardwareEphemeridesList了");
      if(hardwareGpsNavMessageProto.iono == null){
        Log.d("测试解调","hardwareGpsNavMessageProto.iono为空");
      }
      if (hardwareGpsNavMessageProto.iono != null) {
        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
          if (usefulSatellitesToReceiverMeasurements[i] != null) {
            int prn = i + 1;
            Log.d("测试解调",prn+"卫星的usefulSatellitesToReceiverMeasurements非空");
            for (GpsEphemerisProto hardwareEphProtoFromList : hardwareEphemeridesList) {
              if (hardwareEphProtoFromList.prn == prn) {
                Log.d("测试解调",prn+"卫星的星历被找到了");
                ++count;
                break;
              }
            }
          }
          else {
            Log.d("测试解调",(i+1)+"卫星的usefulSatellitesToReceiverMeasurements为空");
          }
        }
      }
    }
    Log.d(TAG, "收到" + count + "个GPS卫星的星历");
    return count >= MINIMUM_NUMBER_OF_USEFUL_SATELLITES;
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
      Log.d(TAG, "测试解调:in parseHwNavigationMessageUpdates: messagePrn="+messagePrn
      +"    messageType="+messageType+"   subMessageId="+subMessageId);
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
  public double[] getPositionSolutionLatLngDeg() {
    return mPositionSolutionLatLngDeg;
  }

  /**
   * Returns the pseudoranges
   */
  public double[] getPseudorangesMeters() {
    return mPseudorangesMeters;
  }
}
