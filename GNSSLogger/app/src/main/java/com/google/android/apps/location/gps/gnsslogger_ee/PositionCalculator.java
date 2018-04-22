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

package com.google.android.apps.location.gps.gnsslogger_ee;

import android.graphics.Color;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger_ee.ResultFragment.UIResultComponent;
import com.google.location.lbs.gnss.gps.pseudorange.PseudorangePositionFromRealTimeEvents;

import java.text.DecimalFormat;

/**
 * A class that handles real time position calculation, passing {@link GnssMeasurementsEvent}
 * instances to the {@link PseudorangePositionFromRealTimeEvents} whenever a new raw
 * measurement is received in order to compute a new position solution. The computed
 * position solutions are passed to the {@link ResultFragment} to be visualized.
 */
public class PositionCalculator implements GnssListener {

  private static final long EARTH_RADIUS_METERS = 6371000;
  private PseudorangePositionFromRealTimeEvents
      mPseudorangePositionFromRealTimeEvents;
  private HandlerThread mPositionCalculationHandlerThread;
  private Handler mMyPositionCalculationHandler;
  private int mCurrentColor = Color.rgb(0x4a, 0x5f, 0x70);
  private int mCurrentColorIndex = 0;
  private boolean mAllowShowingRawResults = false;
  private MainActivity mMainActivity;
  private int[] mRgbColorArray = {
    Color.rgb(0x4a, 0x5f, 0x70),
    Color.rgb(0x7f, 0x82, 0x5f),
    Color.rgb(0xbf, 0x90, 0x76),
    Color.rgb(0x82, 0x4e, 0x4e),
    Color.rgb(0x66, 0x77, 0x7d)
  };
  private double latitude = Double.NaN;
  private double longitude = Double.NaN;

  public PositionCalculator() {
    mPositionCalculationHandlerThread =
        new HandlerThread("Position From Realtime Pseudoranges");
    mPositionCalculationHandlerThread.start();
    mMyPositionCalculationHandler =
        new Handler(mPositionCalculationHandlerThread.getLooper());

    final Runnable r =
        new Runnable() {
          @Override
          public void run() {
            try {
              mPseudorangePositionFromRealTimeEvents =
                  new PseudorangePositionFromRealTimeEvents();
            } catch (Exception e) {
              Log.e(
                  GnssContainer.TAG,
                  " Exception in constructing PseudorangePositionFromRealTimeEvents : ",
                  e);
            }
          }
        };

    mMyPositionCalculationHandler.post(r);
  }

  private UIResultComponent uiResultComponent;

  public synchronized UIResultComponent getUiResultComponent() {
    return uiResultComponent;
  }

  public synchronized void setUiResultComponent(UIResultComponent value) {
    uiResultComponent = value;
  }

  @Override
  public void onProviderEnabled(String provider) {}

  @Override
  public void onProviderDisabled(String provider) {}

  @Override
  public void onGnssStatusChanged(GnssStatus gnssStatus) {}

  /**
   * Update the reference location in {@link PseudorangePositionFromRealTimeEvents} if the
   * received location is a network location. Otherwise, update the {@link ResultFragment} to
   * visualize both GPS location computed by the device and the one computed from the raw data.
   */
  @Override
  public void onLocationChanged(final Location location) {
    if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
      final Runnable r =
          new Runnable() {
            @Override
            public void run() {
              if (mPseudorangePositionFromRealTimeEvents == null) {
                return;
              }
              try {
                mPseudorangePositionFromRealTimeEvents.setReferencePosition(
                    (int) (location.getLatitude() * 1E7),
                    (int) (location.getLongitude() * 1E7),
                    (int) (location.getAltitude() * 1E7));
              } catch (Exception e) {
                Log.e(GnssContainer.TAG, " Exception setting reference location : ", e);
              }
            }
          };
      mMyPositionCalculationHandler.post(r);
    } else if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
      if (mAllowShowingRawResults) {
        final Runnable r =
            new Runnable() {
              @Override
              public void run() {
                if (mPseudorangePositionFromRealTimeEvents == null) {
                  return;
                }

                try {
                  //先将设备返回的位置设为参考位置，用于请求服务器的星历数据
                  mPseudorangePositionFromRealTimeEvents.setReferencePosition(
                      (int) (location.getLatitude() * 1E7),
                      (int) (location.getLongitude() * 1E7),
                      (int) (location.getAltitude() * 1E7));
                } catch (Exception e) {
                  Log.e(GnssContainer.TAG, " Exception setting reference location : ", e);
                }
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                //logLocationEvent("onLocationChanged: " + location);
              }
            };
        mMyPositionCalculationHandler.post(r);
      }
    }
  }

  @Override
  public void onLocationStatusChanged(String provider, int status, Bundle extras) {}

  @Override
  public void onGnssMeasurementsReceived(final GnssMeasurementsEvent event) {
    mAllowShowingRawResults = true;
    final Runnable r =
        new Runnable() {
          @Override
          public void run() {
            mMainActivity.runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                  }
                });
            if (mPseudorangePositionFromRealTimeEvents == null) {
              return;
            }
            try {
              mPseudorangePositionFromRealTimeEvents
                  .computePositionSolutionsFromRawMeas(event);
              double[] posSolution =
                  mPseudorangePositionFromRealTimeEvents.getPositionSolutionLatLngDeg();
              if (Double.isNaN(posSolution[0])) {
                logPositionFromRawDataEvent("No Position Calculated Yet");
                logPositionError("And no offset calculated yet...");
              } else {
                String formattedLatDegree = new DecimalFormat("##.######").format(posSolution[0]);
                String formattedLngDegree = new DecimalFormat("##.######").format(posSolution[1]);
                String formattedAltMeters = new DecimalFormat("##.#").format(posSolution[2]);
                logPositionFromRawDataEvent(
                    "latDegrees = "
                        + formattedLatDegree
                        + " lngDegrees = "
                        + formattedLngDegree
                        + " altMeters = "
                        + formattedAltMeters);
                String formattedOffsetMeters;
                if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                  formattedOffsetMeters = "NaN";
                } else {
                  formattedOffsetMeters =
                      new DecimalFormat("##.######")
                          .format(
                              getDistanceMeters(
                                  latitude,
                                  longitude,
                                  posSolution[0],
                                  posSolution[1]));
                }
                logPositionError("position offset = " + formattedOffsetMeters + " meters");
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
    mMyPositionCalculationHandler.post(r);
  }

  @Override
  public void onGnssMeasurementsStatusChanged(int status) {}

  @Override
  public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
    if (event.getType() == GnssNavigationMessage.TYPE_GPS_L1CA) {
      mPseudorangePositionFromRealTimeEvents.parseHwNavigationMessageUpdates(event);
    }
  }

  @Override
  public void onGnssNavigationMessageStatusChanged(int status) {}

  @Override
  public void onNmeaReceived(long l, String s) {}

  @Override
  public void onListenerRegistration(String listener, boolean result) {}

  private void logEvent(String tag, String message, int color) {
    String composedTag = GnssContainer.TAG + tag;
    Log.d(composedTag, message);
    logText(tag, message, color);
  }

  private void logText(String tag, String text, int color) {
    UIResultComponent component = getUiResultComponent();
    if (component != null) {
      component.logTextResults(tag, text, color);
    }
  }

  public void logLocationEvent(String event) {
    mCurrentColor = getNextColor();
    logEvent("Location", event, mCurrentColor);
  }

  private void logPositionFromRawDataEvent(String event) {
    logEvent("Calculated WLS Position From Raw Data", event + "\n", mCurrentColor);
  }

  private void logPositionError(String event) {
    logEvent(
        "Offset between the position calculated using WLS based on reported measurements and "
            + "the position reported by the device", event + "\n",
        mCurrentColor);
  }

  private synchronized int getNextColor() {
    ++mCurrentColorIndex;
    return mRgbColorArray[mCurrentColorIndex % mRgbColorArray.length];
  }

  /**
   * Return the distance (measured along the surface of the sphere) between 2 points
   */
  public double getDistanceMeters(
      double lat1Degree, double lng1Degree, double lat2Degree, double lng2Degree) {

    double deltaLatRadian = Math.toRadians(lat2Degree - lat1Degree);
    double deltaLngRadian = Math.toRadians(lng2Degree - lng1Degree);

    double a =
        Math.sin(deltaLatRadian / 2) * Math.sin(deltaLatRadian / 2)
            + Math.cos(Math.toRadians(lat1Degree))
                * Math.cos(Math.toRadians(lat2Degree))
                * Math.sin(deltaLngRadian / 2)
                * Math.sin(deltaLngRadian / 2);
    double angularDistanceRad = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return EARTH_RADIUS_METERS * angularDistanceRad;
  }

  /**
   * Sets {@link MainActivity} for running some UI tasks on UI thread
   */
  public void setMainActivity(MainActivity mainActivity) {
    this.mMainActivity = mainActivity;
  }


  @Override
  public void onTTFFReceived(long l) {}
}
