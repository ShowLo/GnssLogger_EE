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
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.google.location.lbs.gnss.gps.pseudorange.SimulationGpsClock;
import com.google.location.lbs.gnss.gps.pseudorange.SimulationGpsMeasurement;
import com.google.location.lbs.gnss.gps.pseudorange.SimulationGpsMeasurementsEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;



/**
 * A container for GPS related API calls, it binds the {@link LocationManager} with {@link UiLogger}
 */
public class GnssContainer {

  public static final String TAG = "GnssLogger";

  private static final long LOCATION_RATE_GPS_MS = TimeUnit.SECONDS.toMillis(1L);
  private static final long LOCATION_RATE_NETWORK_MS = TimeUnit.SECONDS.toMillis(60L);

  private boolean mLogLocations = true;
  private boolean mLogNavigationMessages = true;
  private boolean mLogMeasurements = true;
  private boolean mLogStatuses = true;
  private boolean mLogNmeas = true;
  private long registrationTimeNanos = 0L;
  private long firstLocatinTimeNanos = 0L;
  private long ttff = 0L;
  private boolean firstTime = true;

  private static Context mContext = null;
  private BufferedReader bufferedReader;

  private final List<GnssListener> mLoggers;

  private SimulationCalculator simulationCalculator;

  private final LocationManager mLocationManager;
  private final LocationListener mLocationListener =
      new LocationListener() {

        @Override
        public void onProviderEnabled(String provider) {
          if (mLogLocations) {
            for (GnssListener logger : mLoggers) {
              logger.onProviderEnabled(provider);
            }
          }
        }

        @Override
        public void onProviderDisabled(String provider) {
          if (mLogLocations) {
            for (GnssListener logger : mLoggers) {
              logger.onProviderDisabled(provider);
            }
          }
        }

        @Override
        public void onLocationChanged(Location location) {
          if (firstTime && location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            if (mLogLocations) {
              for (GnssListener logger : mLoggers) {
                firstLocatinTimeNanos = SystemClock.elapsedRealtimeNanos();
                ttff = firstLocatinTimeNanos - registrationTimeNanos;
                logger.onTTFFReceived(ttff);
              }
            }
            firstTime = false;
          }
          if (mLogLocations) {
            for (GnssListener logger : mLoggers) {
              logger.onLocationChanged(location);
            }
          }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
          if (mLogLocations) {
            for (GnssListener logger : mLoggers) {
              logger.onLocationStatusChanged(provider, status, extras);
            }
          }
        }
      };

  private final GnssMeasurementsEvent.Callback gnssMeasurementsEventListener =
      new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
          if (mLogMeasurements) {
            for (GnssListener logger : mLoggers) {
              logger.onGnssMeasurementsReceived(event);
            }
          }
        }

        @Override
        public void onStatusChanged(int status) {
          if (mLogMeasurements) {
            for (GnssListener logger : mLoggers) {
              logger.onGnssMeasurementsStatusChanged(status);
            }
          }
        }
      };

  private final GnssNavigationMessage.Callback gnssNavigationMessageListener =
      new GnssNavigationMessage.Callback() {
        @Override
        public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
          if (mLogNavigationMessages) {
            for (GnssListener logger : mLoggers) {
              logger.onGnssNavigationMessageReceived(event);
            }
          }
        }

        @Override
        public void onStatusChanged(int status) {
          if (mLogNavigationMessages) {
            for (GnssListener logger : mLoggers) {
              logger.onGnssNavigationMessageStatusChanged(status);
            }
          }
        }
      };

  private final GnssStatus.Callback gnssStatusListener =
      new GnssStatus.Callback() {
        @Override
        public void onStarted() {}

        @Override
        public void onStopped() {}

        @Override
        public void onFirstFix(int ttff) {}

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
          for (GnssListener logger : mLoggers) {
            logger.onGnssStatusChanged(status);
          }
        }
      };

  private final OnNmeaMessageListener nmeaListener =
      new OnNmeaMessageListener() {
        @Override
        public void onNmeaMessage(String s, long l) {
          if (mLogNmeas) {
            for (GnssListener logger : mLoggers) {
              logger.onNmeaReceived(l, s);
            }
          }
        }
      };

  public GnssContainer(Context context, GnssListener... loggers) {
    this.mContext = context;
    this.mLoggers = Arrays.asList(loggers);
    mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  public void setSimulationCalculator(SimulationCalculator simulationCalculator) {
    this.simulationCalculator = simulationCalculator;
  }

  public LocationManager getLocationManager() {
    return mLocationManager;
  }

  public void setLogLocations(boolean value) {
    mLogLocations = value;
  }

  public boolean canLogLocations() {
    return mLogLocations;
  }

  public void setLogNavigationMessages(boolean value) {
    mLogNavigationMessages = value;
  }

  public boolean canLogNavigationMessages() {
    return mLogNavigationMessages;
  }

  public void setLogMeasurements(boolean value) {
    mLogMeasurements = value;
  }

  public boolean canLogMeasurements() {
    return mLogMeasurements;
  }

  public void setLogStatuses(boolean value) {
    mLogStatuses = value;
  }

  public boolean canLogStatuses() {
    return mLogStatuses;
  }

  public void setLogNmeas(boolean value) {
    mLogNmeas = value;
  }

  public boolean canLogNmeas() {
    return mLogNmeas;
  }

  public void registerLocation() {
    boolean isGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    if (isGpsProviderEnabled) {
      mLocationManager.requestLocationUpdates(
          LocationManager.NETWORK_PROVIDER,
          LOCATION_RATE_NETWORK_MS,
          0.0f /* minDistance */,
          mLocationListener);
      mLocationManager.requestLocationUpdates(
          LocationManager.GPS_PROVIDER,
          LOCATION_RATE_GPS_MS,
          0.0f /* minDistance */,
          mLocationListener);
    }
    logRegistration("LocationUpdates", isGpsProviderEnabled);
  }

  public void registerSingleNetworkLocation() {
    boolean isNetworkProviderEnabled =
        mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    if (isNetworkProviderEnabled) {
      mLocationManager.requestSingleUpdate(
          LocationManager.NETWORK_PROVIDER, mLocationListener, null);
    }
    logRegistration("LocationUpdates", isNetworkProviderEnabled);
  }

  public void registerSingleGpsLocation() {
    boolean isGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    if (isGpsProviderEnabled) {
      this.firstTime = true;
      registrationTimeNanos = SystemClock.elapsedRealtimeNanos();
      mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, null);
    }
    logRegistration("LocationUpdates", isGpsProviderEnabled);
  }

  public void unregisterLocation() {
    mLocationManager.removeUpdates(mLocationListener);
  }

  public void registerMeasurements() {
    logRegistration(
        "GnssMeasurements",
        mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventListener));
  }

  public void unregisterMeasurements() {
    mLocationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener);
  }

  public void registerNavigation() {
    logRegistration(
        "GpsNavigationMessage",
        mLocationManager.registerGnssNavigationMessageCallback(gnssNavigationMessageListener));
  }

  public void unregisterNavigation() {
    mLocationManager.unregisterGnssNavigationMessageCallback(gnssNavigationMessageListener);
  }

  public void registerGnssStatus() {
    logRegistration("GnssStatus", mLocationManager.registerGnssStatusCallback(gnssStatusListener));
  }

  public void unregisterGpsStatus() {
    mLocationManager.unregisterGnssStatusCallback(gnssStatusListener);
  }

  public void registerNmea() {
    logRegistration("Nmea", mLocationManager.addNmeaListener(nmeaListener));
  }

  public void unregisterNmea() {
    mLocationManager.removeNmeaListener(nmeaListener);
  }

  public void startSimulation() {
    InputStream inputStream = mContext.getResources().openRawResource(R.raw.gnss_log_2018_05_09_15_47_23);
    InputStreamReader in = new InputStreamReader(inputStream);
    bufferedReader = new BufferedReader(in);
    new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          String line = null;
          if (bufferedReader != null) {
            line = bufferedReader.readLine();
          }
          while (bufferedReader != null && line != null) {
            if (line.substring(0, 1).contains("R")) {
              System.out.println(line);
              String[] datas = line.split(",", -1);
              SimulationGpsMeasurementsEvent gpsMeasurementsEvent = new SimulationGpsMeasurementsEvent();
              SimulationGpsClock gpsClock = new SimulationGpsClock();

              long elapsedRealtimeMillis = Long.parseLong(datas[1]);
              long timeNanos = Long.parseLong(datas[2]);
              gpsClock.setTimeNanos(timeNanos);
              if (datas[3].equals("")) {
                gpsClock.setHasLeapSecond(false);
              } else {
                gpsClock.setHasLeapSecond(true);
                gpsClock.setLeapSecond(Integer.parseInt(datas[3]));
              }
              if (datas[4].equals("")) {
                gpsClock.setHasTimeUncertaintyNanos(false);
              } else {
                gpsClock.setHasTimeUncertaintyNanos(true);
                gpsClock.setTimeUncertaintyNanos(Double.parseDouble(datas[4]));
              }
              if (datas[5].equals("")) {
                gpsClock.setHasFullBiasNanos(false);
              } else {
                gpsClock.setHasFullBiasNanos(true);
                gpsClock.setFullBiasNanos(Long.parseLong(datas[5]));
              }
              if (datas[6].equals("")) {
                gpsClock.setHasBiasNanos(false);
              } else {
                gpsClock.setHasBiasNanos(true);
                gpsClock.setBiasNanos(Double.parseDouble(datas[6]));
              }
              if (datas[7].equals("")) {
                gpsClock.setHasBiasUncertaintyNanos(false);
              } else {
                gpsClock.setHasBiasUncertaintyNanos(true);
                gpsClock.setBiasUncertaintyNanos(Double.parseDouble(datas[7]));
              }
              if (datas[8].equals("")) {
                gpsClock.setHasDriftNanosPerSecond(false);
              } else {
                gpsClock.setHasDriftNanosPerSecond(true);
                gpsClock.setDriftNanosPerSecond(Double.parseDouble(datas[8]));
              }
              if (datas[9].equals("")) {
                gpsClock.setHasDriftUncertaintyNanosPerSecond(false);
              } else {
                gpsClock.setHasDriftUncertaintyNanosPerSecond(true);
                gpsClock.setDriftUncertaintyNanosPerSecond(Double.parseDouble(datas[9]));
              }
              gpsClock.setHardwareClockDiscontinuityCount(Integer.parseInt(datas[10]));

              gpsMeasurementsEvent.setSimulationGpsClock(gpsClock);

              List<SimulationGpsMeasurement> simulationGpsMeasurements = new ArrayList<>();

              // 相同的timeNanos代表属于同一个时间捕获的measurement
              String sameGroupMark = datas[2];
              while (sameGroupMark.equals(datas[2])) {
                SimulationGpsMeasurement gpsMeasurement = new SimulationGpsMeasurement();
                gpsMeasurement.setSvid(Integer.parseInt(datas[11]));
                gpsMeasurement.setTimeOffsetNanos(Double.parseDouble(datas[12]));
                gpsMeasurement.setState(Integer.parseInt(datas[13]));
                gpsMeasurement.setReceivedSvTimeNanos(Long.parseLong(datas[14]));
                gpsMeasurement.setReceivedSvTimeUncertaintyNanos(Long.parseLong(datas[15]));
                gpsMeasurement.setCn0DbHz(Double.parseDouble(datas[16]));
                gpsMeasurement.setPseudorangeRateMetersPerSecond(Double.parseDouble(datas[17]));
                gpsMeasurement.setPseudorangeRateUncertaintyMetersPerSecond(Double.parseDouble(datas[18]));
                gpsMeasurement.setAccumulatedDeltaRangeState(Integer.parseInt(datas[19]));
                gpsMeasurement.setAccumulatedDeltaRangeMeters(Double.parseDouble(datas[20]));
                gpsMeasurement.setAccumulatedDeltaRangeUncertaintyMeters(Double.parseDouble(datas[21]));
                if (datas[22].equals("")) {
                  gpsMeasurement.setHasCarrierFrequencyHz(false);
                } else {
                  gpsMeasurement.setHasCarrierFrequencyHz(true);
                  gpsMeasurement.setCarrierFrequencyHz(Float.parseFloat(datas[22]));
                }
                if (datas[23].equals("")) {
                  gpsMeasurement.setHasCarrierCycles(false);
                } else {
                  gpsMeasurement.setHasCarrierCycles(true);
                  gpsMeasurement.setCarrierCycles(Long.parseLong(datas[23]));
                }
                if (datas[24].equals("")) {
                  gpsMeasurement.setHasCarrierPhase(false);
                } else {
                  gpsMeasurement.setHasCarrierPhase(true);
                  gpsMeasurement.setCarrierPhase(Double.parseDouble(datas[24]));
                }
                if (datas[25].equals("")) {
                  gpsMeasurement.setHasCarrierPhaseUncertainty(false);
                } else {
                  gpsMeasurement.setHasCarrierPhaseUncertainty(true);
                  gpsMeasurement.setCarrierPhaseUncertainty(Double.parseDouble(datas[25]));
                }
                gpsMeasurement.setMultipathIndicator(Integer.parseInt(datas[26]));
                if (datas[27].equals("")) {
                  gpsMeasurement.setHasSnrInDb(false);
                } else {
                  gpsMeasurement.setHasSnrInDb(true);
                  gpsMeasurement.setSnrInDb(Double.parseDouble(datas[27]));
                }
                gpsMeasurement.setConstellationType(Integer.parseInt(datas[28]));
                if (datas[29].equals("")) {
                  gpsMeasurement.setHasAutomaticGainControlLevelDb(false);
                } else {
                  gpsMeasurement.setHasAutomaticGainControlLevelDb(true);
                  gpsMeasurement.setAutomaticGainControlLevelDb(Double.parseDouble(datas[29]));
                }

                simulationGpsMeasurements.add(gpsMeasurement);

                if (bufferedReader != null) {
                  line = bufferedReader.readLine();
                  if (line != null) {
                    if (line.substring(0, 1).contains("R")) {
                      datas = line.split(",", -1);
                    } else {
                      break;
                    }
                  } else {
                    break;
                  }
                } else {
                  break;
                }
              }
              gpsMeasurementsEvent.setSimulationGpsMeasurements(
                  Collections.unmodifiableCollection(simulationGpsMeasurements));

              simulationCalculator.onGnssMeasurementsReceived(
                  gpsMeasurementsEvent
              );

              Thread.sleep(5000);
            } else {
              line = bufferedReader.readLine();
            }
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (IOException ioe) {
          Log.d(TAG, "读取模拟输入文件出错");
          ioe.printStackTrace();
        }
      }
    }.start();
  }

  public void stopSimulation() {
    if (bufferedReader != null) {
      try {
        bufferedReader.close();
        bufferedReader = null;
      } catch (IOException ioe) {
        Log.d(TAG, "关闭模拟输入文件出错");
      }
    }
  }

  public void registerAll() {
    registerLocation();
    registerMeasurements();
    registerNavigation();
    registerGnssStatus();
    registerNmea();
  }

  public void unregisterAll() {
    unregisterLocation();
    unregisterMeasurements();
    unregisterNavigation();
    unregisterGpsStatus();
    unregisterNmea();
  }

  private void logRegistration(String listener, boolean result) {
    for (GnssListener logger : mLoggers) {
      logger.onListenerRegistration(listener, result);
    }
  }
}
