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
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger_ee.LoggerOneFragment.UiFragmentComponent;


/**
 * A class representing a UI logger for the application. Its responsibility is show information in
 * the UI.
 */
public class UiLoggerGnssStatus implements GnssListener {

  private static final int USED_COLOR = Color.rgb(0x4a, 0x5f, 0x70);

  public UiLoggerGnssStatus() {}

  private UiFragmentComponent mUiFragmentComponent;

  public synchronized UiFragmentComponent getUiFragmentComponent() {
    return mUiFragmentComponent;
  }

  public synchronized void setUiFragmentComponent(UiFragmentComponent value) {
    mUiFragmentComponent = value;
  }

  @Override
  public void onProviderEnabled(String provider) {}

  @Override
  public void onTTFFReceived(long l) {}

  @Override
  public void onProviderDisabled(String provider) {}

  @Override
  public void onLocationChanged(Location location) {}

  @Override
  public void onLocationStatusChanged(String provider, int status, Bundle extras) {}

  @Override
  public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {}

  @Override
  public void onGnssMeasurementsStatusChanged(int status) {}

  @Override
  public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {}

  @Override
  public void onGnssNavigationMessageStatusChanged(int status) {}

  @Override
  public void onGnssStatusChanged(GnssStatus gnssStatus) {
    logStatusEvent("onGnssStatusChanged: " + gnssStatusToString(gnssStatus));
  }

  @Override
  public void onNmeaReceived(long timestamp, String s) {}

  @Override
  public void onListenerRegistration(String listener, boolean result) {}

  private void logStatusEvent(String event) {
    logEvent("Status", event, USED_COLOR);
  }

  private void logEvent(String tag, String message, int color) {
    String composedTag = GnssContainer.TAG + tag;
    Log.d(composedTag, message);
    logText(tag, message, color);
  }

  private void logText(String tag, String text, int color) {
    UiFragmentComponent component = getUiFragmentComponent();
    if (component != null) {
      component.logTextFragment(tag, text, color);
    }
  }

  private String gnssStatusToString(GnssStatus gnssStatus) {

    StringBuilder builder = new StringBuilder("SATELLITE_STATUS | [Satellites:\n");
    for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
      builder
          .append("Constellation = ")
          .append(getConstellationName(gnssStatus.getConstellationType(i)))
          .append(", ");
      builder.append("Svid = ").append(gnssStatus.getSvid(i)).append(", ");
      builder.append("Cn0DbHz = ").append(gnssStatus.getCn0DbHz(i)).append(", ");
      builder.append("Elevation = ").append(gnssStatus.getElevationDegrees(i)).append(", ");
      builder.append("Azimuth = ").append(gnssStatus.getAzimuthDegrees(i)).append(", ");
      builder.append("hasEphemeris = ").append(gnssStatus.hasEphemerisData(i)).append(", ");
      builder.append("hasAlmanac = ").append(gnssStatus.hasAlmanacData(i)).append(", ");
      builder.append("usedInFix = ").append(gnssStatus.usedInFix(i)).append("\n");
    }
    builder.append("]");
    return builder.toString();
  }

  private String getConstellationName(int id) {
    switch (id) {
      case 1:
        return "GPS";
      case 2:
        return "SBAS";
      case 3:
        return "GLONASS";
      case 4:
        return "QZSS";
      case 5:
        return "BEIDOU";
      case 6:
        return "GALILEO";
      default:
        return "UNKNOWN";
    }
  }
}
