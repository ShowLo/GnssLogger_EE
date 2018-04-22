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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger_ee.TestFragment.UiTestFragmentComponent;
import com.google.location.lbs.gnss.gps.pseudorange.TestEvents;

import java.text.DecimalFormat;

public class TestCalculator implements GnssListener {

  private static final long EARTH_RADIUS_METERS = 6371000;
  private TestEvents mTestEvents;
  private HandlerThread mTestHandlerThread;
  private Handler mTestHandler;
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

  public TestCalculator() {
    mTestHandlerThread =
        new HandlerThread("Pseudolite Positioning");
    mTestHandlerThread.start();
    mTestHandler =
        new Handler(mTestHandlerThread.getLooper());

    final Runnable r =
        new Runnable() {
          @Override
          public void run() {
            try {
              mTestEvents =
                  new TestEvents();
            } catch (Exception e) {
              Log.e(
                  GnssContainer.TAG,
                  " Exception in constructing TestEvents : ",
                  e);
            }
          }
        };

    mTestHandler.post(r);
  }

  private UiTestFragmentComponent uiTestComponent;

  public synchronized UiTestFragmentComponent getUiTestComponent() {
    return uiTestComponent;
  }

  public synchronized void setUiTestComponent(UiTestFragmentComponent value) {
    uiTestComponent = value;
  }

  @Override
  public void onProviderEnabled(String provider) {}

  @Override
  public void onProviderDisabled(String provider) {}

  @Override
  public void onGnssStatusChanged(GnssStatus gnssStatus) {}

  @Override
  public void onLocationChanged(final Location location) {}

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
            if (mTestEvents == null) {
              return;
            }
            try {
              mTestEvents
                  .computePseudolitePositioningSolutionsFromRawMeas();
              double[] posSolution =
                  mTestEvents.getPseudolitePositioningSolutionXYZ();
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
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
    mTestHandler.post(r);
  }

  @Override
  public void onGnssMeasurementsStatusChanged(int status) {}

  @Override
  public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {}

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
    UiTestFragmentComponent component = getUiTestComponent();
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
