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

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger_ee.SimulationFragment.UiSimulationFragmentComponent;
import com.google.location.lbs.gnss.gps.pseudorange.PseudolitePositioningFromSimulationEvents;
import com.google.location.lbs.gnss.gps.pseudorange.SimulationGpsMeasurementsEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

/**
 * A class that handles simulation, passing {@link SimulationGpsMeasurementsEvent}
 * instances to the {@link PseudolitePositioningFromSimulationEvents} whenever a
 * measurement is received in order to compute a new position solution. The computed
 * position solutions are passed to the {@link SimulationFragment} to be visualized.
 */
public class SimulationCalculator {

  private static final long EARTH_RADIUS_METERS = 6371000;
  private PseudolitePositioningFromSimulationEvents
      mPseudolitePositioningFromSimulationEvents;
  private HandlerThread mPositionCalculationHandlerThread;
  private Handler mMyPositionCalculationHandler;
  private int mCurrentColor = Color.rgb(0x4a, 0x5f, 0x70);
  private int mCurrentColorIndex = 0;
  private boolean mAllowShowingRawResults = false;
  private MainActivity mMainActivity;
  private static Context mContext = null;
  /*private PlotPseudoliteFragment mPlotPseudoliteFragment;
  private int[] mRgbColorArray = {
    Color.rgb(0x4a, 0x5f, 0x70),
    Color.rgb(0x7f, 0x82, 0x5f),
    Color.rgb(0xbf, 0x90, 0x76),
    Color.rgb(0x82, 0x4e, 0x4e),
    Color.rgb(0x66, 0x77, 0x7d)
  };

  public void setPlotPseudoliteFragment(PlotPseudoliteFragment plotPseudoliteFragment) {
    this.mPlotPseudoliteFragment = plotPseudoliteFragment;
  }*/

  private FileLoggerSimulation mFileLoggerSimulation;

  public synchronized void setFileLoggerSimulation(FileLoggerSimulation value) {
    mFileLoggerSimulation = value;
  }

  public SimulationCalculator(Context context) {
    this.mContext = context;
    mPositionCalculationHandlerThread =
        new HandlerThread("Pseudolite Positioning for simulation");
    mPositionCalculationHandlerThread.start();
    mMyPositionCalculationHandler =
        new Handler(mPositionCalculationHandlerThread.getLooper());

    final Runnable r =
        new Runnable() {
          @Override
          public void run() {
            try {
              mPseudolitePositioningFromSimulationEvents =
                  new PseudolitePositioningFromSimulationEvents();
            } catch (Exception e) {
              Log.e(
                  GnssContainer.TAG,
                  " Exception in constructing PseudolitePositioningFromSimulationEvents : ",
                  e);
            }
          }
        };

    mMyPositionCalculationHandler.post(r);
  }

  private UiSimulationFragmentComponent uiSimulationComponent;

  public synchronized UiSimulationFragmentComponent getUiSimulationComponent() {
    return uiSimulationComponent;
  }

  public synchronized void setUiSimulationComponent(UiSimulationFragmentComponent value) {
    uiSimulationComponent = value;
  }

  public void onGnssMeasurementsReceived(final SimulationGpsMeasurementsEvent event) {
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
            if (mPseudolitePositioningFromSimulationEvents == null) {
              return;
            }
            try {
              mPseudolitePositioningFromSimulationEvents.setEph(readEph());
              mPseudolitePositioningFromSimulationEvents
                  .computePseudolitePositioningSolutionsFromSimulation(event);

              mMainActivity.runOnUiThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      long timeSeconds = TimeUnit.NANOSECONDS.toSeconds(event.getClock().getTimeNanos());
                      /*mPlotPseudoliteFragment.updateRawPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getRawPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateAntennaToSatPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getAntennaToSatPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateAntennaToUserPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getAntennaToUserPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateChangeOfRawPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getChangeOfRawPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateChangeOfAntennaToSatPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getChangeOfAntennaToSatPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateChangeOfAntennaToUserPseudorangesTab(
                          mPseudolitePositioningFromRealTimeEvents.getChangeOfAntennaToUserPseudorangesMeters(), timeSeconds);
                      mPlotPseudoliteFragment.updateRawPseudorangesRateTab(
                          mPseudolitePositioningFromRealTimeEvents.getRawPseudorangesRateMps(), timeSeconds);
                      mPlotPseudoliteFragment.updateAntennaToSatPseudorangesRateTab(
                          mPseudolitePositioningFromRealTimeEvents.getAntennaToSatPseudorangesRateMps(), timeSeconds);
                      mPlotPseudoliteFragment.updateAntennaToUserPseudorangesRateTab(
                          mPseudolitePositioningFromRealTimeEvents.getAntennaToUserPseudorangesRateMps(), timeSeconds);*/
                    }
                  }
              );

              double[] posSolution =
                  mPseudolitePositioningFromSimulationEvents.getPseudolitePositioningSolutionXYZ();
              if (Double.isNaN(posSolution[0])) {
                logPseudolitePositioningFromSimulationEvent("No result Calculated Yet");
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
                logPseudolitePositioningFromSimulationEvent(message);
                if (mFileLoggerSimulation.getEnableWrite()) {
                  mFileLoggerSimulation.writeNewMessage(message);
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
    mMyPositionCalculationHandler.post(r);
  }

  private void logEvent(String tag, String message, int color) {
    String composedTag = GnssContainer.TAG + tag;
    Log.d(composedTag, message);
    logText(tag, message, color);
  }

  private void logText(String tag, String text, int color) {
    UiSimulationFragmentComponent component = getUiSimulationComponent();
    if (component != null) {
      component.logTextFragment(tag, text, color);
    }
  }

  private void logPseudolitePositioningFromSimulationEvent(String event) {
    logEvent("Calculated the result of pseudolite positioning From Simulation", event + "\n", mCurrentColor);
  }

  /**
   * Sets {@link MainActivity} for running some UI tasks on UI thread
   */
  public void setMainActivity(MainActivity mainActivity) {
    this.mMainActivity = mainActivity;
  }

  protected String[] readEph() {
    int satelliteNum = 7;
    int oneSatelliteLineNum = 8;
    String[] eph = new String[satelliteNum * oneSatelliteLineNum];
    String line;
    boolean hasPassHeader = false;

    int lineCount = 0;

    try {

      InputStream inputStream = mContext.getResources().openRawResource(R.raw.ephl1_2018_01_18_a1);
      InputStreamReader in = new InputStreamReader(inputStream);
      BufferedReader br = new BufferedReader(in);

      while (lineCount < satelliteNum * oneSatelliteLineNum) {
        line = br.readLine();
        if (!hasPassHeader) {
          hasPassHeader = line.contains("END OF HEADER");
        } else {
          eph[lineCount++] = line;
        }
      }
      br.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      Log.d("SimulationCalculator", "读取星历文件出错");
      return null;
    }

    return eph;
  }

}
