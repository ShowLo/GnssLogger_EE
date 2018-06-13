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
import com.google.location.lbs.gnss.gps.pseudorange.PseudoliteMessageStore;
import com.google.location.lbs.gnss.gps.pseudorange.PseudolitePositioningFromSimulationEvents;
import com.google.location.lbs.gnss.gps.pseudorange.SimulationGpsMeasurementsEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
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
  private PlotPseudoliteFragment mPlotSimulationFragment;
  private int[] mRgbColorArray = {
    Color.rgb(0x4a, 0x5f, 0x70),
    Color.rgb(0x7f, 0x82, 0x5f),
    Color.rgb(0xbf, 0x90, 0x76),
    Color.rgb(0x82, 0x4e, 0x4e),
    Color.rgb(0x66, 0x77, 0x7d)
  };

  public void setPlotSimulationFragment(PlotPseudoliteFragment plotSimulationFragment) {
    this.mPlotSimulationFragment = plotSimulationFragment;
  }

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
              // 从文件读星历并设置
              mPseudolitePositioningFromSimulationEvents.setEph(readEph());
              // 从文件读伪卫星信息并设置
              mPseudolitePositioningFromSimulationEvents.setPseudoliteMessageStore(readPseudoliteMessage());
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
              mPseudolitePositioningFromSimulationEvents
                  .computePseudolitePositioningSolutionsFromSimulation(event);

              mMainActivity.runOnUiThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      // 更新各种图
                      long timeSeconds = TimeUnit.NANOSECONDS.toSeconds(event.getClock().getTimeNanos());
                      mPlotSimulationFragment.updateCn0DbHzTab(
                          mPseudolitePositioningFromSimulationEvents.getCn0DbHz(), timeSeconds);
                      mPlotSimulationFragment.updateRawPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getRawPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateAntennaToSatPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getAntennaToSatPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateAntennaToUserPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getAntennaToUserPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateChangeOfRawPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getChangeOfRawPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateChangeOfAntennaToSatPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getChangeOfAntennaToSatPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateChangeOfAntennaToUserPseudorangesTab(
                          mPseudolitePositioningFromSimulationEvents.getChangeOfAntennaToUserPseudorangesMeters(), timeSeconds);
                      mPlotSimulationFragment.updateRawPseudorangesRateTab(
                          mPseudolitePositioningFromSimulationEvents.getRawPseudorangesRateMps(), timeSeconds);
                      mPlotSimulationFragment.updateAntennaToSatPseudorangesRateTab(
                          mPseudolitePositioningFromSimulationEvents.getAntennaToSatPseudorangesRateMps(), timeSeconds);
                      mPlotSimulationFragment.updateAntennaToUserPseudorangesRateTab(
                          mPseudolitePositioningFromSimulationEvents.getAntennaToUserPseudorangesRateMps(), timeSeconds);
                      double[] positionXYZ = mPseudolitePositioningFromSimulationEvents.getPseudolitePositioningSolutionXYZ();
                      mPlotSimulationFragment.updatePositionTab(positionXYZ[0], positionXYZ[1]);
                      mPlotSimulationFragment.updateHeightTab(positionXYZ[2]);
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

  // 从文件中读取星历
  protected String[] readEph() {
    int oneSatelliteLineNum = 8;
    ArrayList<String> ephList = new ArrayList<>();
    String line;
    boolean hasPassHeader = false;

    try {

      InputStream inputStream = mContext.getResources().openRawResource(R.raw.ephl1_2018_01_18_a1);
      InputStreamReader in = new InputStreamReader(inputStream);
      BufferedReader br = new BufferedReader(in);

      HashSet<Integer> set = new HashSet<>();
      line = br.readLine();
      while (line != null) {
        if (!hasPassHeader) {
          hasPassHeader = line.contains("END OF HEADER");
          line = br.readLine();
        } else {
          int prn = Integer.parseInt(line.substring(0, 2).trim());
          if (set.contains(prn)) {
            for (int i = 0; i < oneSatelliteLineNum && line != null; ++i) {
              line = br.readLine();
            }
          } else {
            set.add(prn);
            ephList.add(line);
            for (int i = 0; i < oneSatelliteLineNum - 1 && line != null; ++i) {
              line = br.readLine();
              ephList.add(line);
            }
            line = br.readLine();
          }
        }
      }
      br.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      Log.d("SimulationCalculator", "读取星历文件出错");
      return null;
    }

    String[] eph = new String[ephList.size()];
    ephList.toArray(eph);

    return eph;
  }

  // 从配置文件中读取伪卫星信息
  protected PseudoliteMessageStore readPseudoliteMessage() {
    PseudoliteMessageStore pseudoliteMessageStore = new PseudoliteMessageStore();

    try {
      InputStream inputStream = mContext.getResources().openRawResource(R.raw.pseudoliteinfo);
      InputStreamReader in = new InputStreamReader(inputStream);
      BufferedReader br = new BufferedReader(in);

      StringBuilder stringBuilder = new StringBuilder();
      String line = br.readLine();
      while(line != null) {
        stringBuilder.append(line + "\n");
        line = br.readLine();
      }

      JSONObject jsonObject = new JSONObject(stringBuilder.toString());
      // 获取室外天线位置
      JSONArray outdoorAntennaLlaArray = jsonObject.getJSONArray("outdoorAntennaLla");
      double[] outdoorAntennaLla = new double[outdoorAntennaLlaArray.length()];
      for(int i = 0; i < outdoorAntennaLlaArray.length(); ++i) {
        outdoorAntennaLla[i] = (Double) outdoorAntennaLlaArray.get(i);
      }
      pseudoliteMessageStore.setOutdoorAntennaLla(outdoorAntennaLla);

      // 获取卫星id
      JSONArray satelliteIdArray = jsonObject.getJSONArray("satelliteId");
      int[] satelliteId = new int[satelliteIdArray.length()];
      for (int i = 0; i < satelliteIdArray.length(); ++i) {
        satelliteId[i] = (Integer) satelliteIdArray.get(i);
      }
      pseudoliteMessageStore.setSatelliteId(satelliteId);

      JSONArray indoorAntennaXyzArray = jsonObject.getJSONArray("indoorAntennaXyz");
      int rowNum = indoorAntennaXyzArray.length();
      int colNum = indoorAntennaXyzArray.getJSONArray(0).length();
      double[][] indoorAntennaXyz = new double[rowNum][colNum];
      for(int i = 0; i < rowNum; ++i) {
        JSONArray indoorAntennaXyzI = indoorAntennaXyzArray.getJSONArray(i);
        for (int j = 0; j < colNum; ++j) {
          indoorAntennaXyz[i][j] = (Double) indoorAntennaXyzI.get(j);
        }
      }
      pseudoliteMessageStore.setIndoorAntennasXyz(indoorAntennaXyz);

      JSONArray outdoorToIndoorRangeArray = jsonObject.getJSONArray("outdoorToIndoorRange");
      double[] outdoorToIndoorRange = new double[outdoorToIndoorRangeArray.length()];
      for (int i = 0; i < outdoorToIndoorRangeArray.length(); ++i) {
        outdoorToIndoorRange[i] = (Double) outdoorToIndoorRangeArray.get(i);
      }
      pseudoliteMessageStore.setOutdoorToIndoorRange(outdoorToIndoorRange);

      JSONArray channelDelayArray = jsonObject.getJSONArray("channelDelay");
      double[] channelDelay = new double[channelDelayArray.length()];
      for (int i = 0; i < channelDelayArray.length(); ++i) {
        channelDelay[i] = (Double) channelDelayArray.get(i);
      }
      pseudoliteMessageStore.setChannelDelay(channelDelay);

    } catch (IOException ioe) {
      ioe.printStackTrace();
      Log.d("SimulationCalculator", "读取伪卫星配置文件出错");
      return null;
    } catch (JSONException je) {
      je.printStackTrace();
      Log.d("SimulationCalculator", "读取伪卫星配置文件构造json出错");
      return null;
    }

    return pseudoliteMessageStore;
  }
}
