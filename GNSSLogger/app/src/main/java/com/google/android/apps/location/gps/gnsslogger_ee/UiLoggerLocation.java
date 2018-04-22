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
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger_ee.LoggerOneFragment.UiFragmentComponent;

/**
 * A class representing a UI logger for the application. Its responsibility is show information in
 * the UI.
 */
public class UiLoggerLocation implements GnssListener {

  private static final int USED_COLOR = Color.rgb(0x4a, 0x5f, 0x70);

  public UiLoggerLocation() {}

  private UiFragmentComponent mUiFragmentComponent;

  public synchronized UiFragmentComponent getUiFragmentComponent() {
    return mUiFragmentComponent;
  }

  public synchronized void setUiFragmentComponent(UiFragmentComponent value) {
      mUiFragmentComponent = value;
  }

  @Override
  public void onProviderEnabled(String provider) {
    logLocationEvent("onProviderEnabled: " + provider);
  }

  @Override
  public void onTTFFReceived(long l) {
    logLocationEvent("TTFF: " + l);
  }

  @Override
  public void onProviderDisabled(String provider) {
    logLocationEvent("onProviderDisabled: " + provider);
  }

  @Override
  public void onLocationChanged(Location location) {
    logLocationEvent("onLocationChanged: " + location + "\n");
  }

  @Override
  public void onLocationStatusChanged(String provider, int status, Bundle extras) {
    String message =
        String.format(
            "onStatusChanged: provider=%s, status=%s, extras=%s",
            provider, locationStatusToString(status), extras);
    logLocationEvent(message);
  }

  @Override
  public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {}

  @Override
  public void onGnssMeasurementsStatusChanged(int status) {}

  @Override
  public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {}

  @Override
  public void onGnssNavigationMessageStatusChanged(int status) {}

  @Override
  public void onGnssStatusChanged(GnssStatus gnssStatus) {}

  @Override
  public void onNmeaReceived(long timestamp, String s) {}

  @Override
  public void onListenerRegistration(String listener, boolean result) {}

  private void logEvent(String tag, String message, int color) {
    String composedTag = GnssContainer.TAG + tag;
    //Log.d(composedTag, message);
    logText(tag, message, color);
  }

  private void logText(String tag, String text, int color) {
      UiFragmentComponent component = getUiFragmentComponent();
    if (component != null) {
      component.logTextFragment(tag, text, color);
    }
  }

  private String locationStatusToString(int status) {
    switch (status) {
      case LocationProvider.AVAILABLE:
        return "AVAILABLE";
      case LocationProvider.OUT_OF_SERVICE:
        return "OUT_OF_SERVICE";
      case LocationProvider.TEMPORARILY_UNAVAILABLE:
        return "TEMPORARILY_UNAVAILABLE";
      default:
        return "<Unknown>";
    }
  }

  private void logLocationEvent(String event) {
    logEvent("Location", event, USED_COLOR);
  }
}
