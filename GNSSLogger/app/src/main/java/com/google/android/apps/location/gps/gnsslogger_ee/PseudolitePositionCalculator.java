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

import com.google.android.apps.location.gps.gnsslogger_ee.PseudoliteFragment.UiPseudoliteFragmentComponent;
import com.google.location.lbs.gnss.gps.pseudorange.PseudolitePositioningFromRealTimeEvents;

import java.text.DecimalFormat;

/**
 * A class that handles real time psdudolite positioning, passing {@link GnssMeasurementsEvent}
 * instances to the {@link PseudolitePositioningFromRealTimeEvents} whenever a new raw
 * measurement is received in order to compute a new position solution. The computed
 * position solutions are passed to the {@link PseudoliteFragment} to be visualized.
 */
public class PseudolitePositionCalculator implements GnssListener {

  private static final long EARTH_RADIUS_METERS = 6371000;
  private PseudolitePositioningFromRealTimeEvents
      mPseudolitePositioningFromRealTimeEvents;
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

  private FileLoggerPseudolite mFileLoggerPseudolite;

  public synchronized void setFileLoggerPseudolite(FileLoggerPseudolite value) {
    mFileLoggerPseudolite = value;
  }

  public PseudolitePositionCalculator() {
    mPositionCalculationHandlerThread =
        new HandlerThread("Pseudolite Positioning");
    mPositionCalculationHandlerThread.start();
    mMyPositionCalculationHandler =
        new Handler(mPositionCalculationHandlerThread.getLooper());

    final Runnable r =
        new Runnable() {
          @Override
          public void run() {
            try {
              mPseudolitePositioningFromRealTimeEvents =
                  new PseudolitePositioningFromRealTimeEvents();
            } catch (Exception e) {
              Log.e(
                  GnssContainer.TAG,
                  " Exception in constructing PseudolitePositioningFromRealTimeEvents : ",
                  e);
            }
          }
        };

    mMyPositionCalculationHandler.post(r);
  }

  private UiPseudoliteFragmentComponent uiPseudoliteComponent;

  public synchronized UiPseudoliteFragmentComponent getUiPseudoliteComponent() {
    return uiPseudoliteComponent;
  }

  public synchronized void setUiPseudoliteComponent(UiPseudoliteFragmentComponent value) {
    uiPseudoliteComponent = value;
  }

  @Override
  public void onProviderEnabled(String provider) {}

  @Override
  public void onProviderDisabled(String provider) {}

  @Override
  public void onGnssStatusChanged(GnssStatus gnssStatus) {}

  /**
   * Update the reference location in {@link PseudolitePositioningFromRealTimeEvents} if the
   * received location is a network location. Otherwise, update the {@link PseudoliteFragment} to
   * visualize the result of pseudolite positioning computed from the raw data.
   */
  @Override
  public void onLocationChanged(final Location location) {
    if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
      final Runnable r =
          new Runnable() {
            @Override
            public void run() {
              if (mPseudolitePositioningFromRealTimeEvents == null) {
                return;
              }
              try {
                mPseudolitePositioningFromRealTimeEvents.setReferencePosition(
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
                if (mPseudolitePositioningFromRealTimeEvents == null) {
                  return;
                }
                double[] posSolution =
                    mPseudolitePositioningFromRealTimeEvents.getPseudolitePositioningSolutionXYZ();
                if (Double.isNaN(posSolution[0])) {
                  logPseudolitePositioningFromRawDataEvent("No result Calculated Yet");
                } else {
                  String formattedXMeters = new DecimalFormat("##.###").format(posSolution[0]);
                  String formattedYMeters = new DecimalFormat("##.###").format(posSolution[1]);
                  String formattedZMeters = new DecimalFormat("##.###").format(posSolution[2]);
                  String message = "xMeters = "
                      + formattedXMeters
                      + " yMeters = "
                      + formattedYMeters
                      + " zMeters = "
                      + formattedZMeters;
                  logPseudolitePositioningFromRawDataEvent(message);
                  if (mFileLoggerPseudolite.getEnableWrite()) {
                    mFileLoggerPseudolite.writeNewMessage(message);
                  }
                }
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
            if (mPseudolitePositioningFromRealTimeEvents == null) {
              return;
            }
            try {
              mPseudolitePositioningFromRealTimeEvents
                  .computePseudolitePositioningSolutionsFromRawMeas(event);
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
      mPseudolitePositioningFromRealTimeEvents.parseHwNavigationMessageUpdates(event);
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
    UiPseudoliteFragmentComponent component = getUiPseudoliteComponent();
    if (component != null) {
      component.logTextFragment(tag, text, color);
    }
  }

  private void logPseudolitePositioningFromRawDataEvent(String event) {
    logEvent("Calculated the result of pseudolite positioning From Raw Data", event + "\n", mCurrentColor);
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
