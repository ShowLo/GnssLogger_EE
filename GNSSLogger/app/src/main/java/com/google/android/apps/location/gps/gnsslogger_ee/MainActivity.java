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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TabLayout.TabLayoutOnPageChangeListener;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import java.util.Locale;

import com.baidu.mapapi.SDKInitializer;

/** The activity for the application. */
public class MainActivity extends AppCompatActivity
    implements OnConnectionFailedListener, ConnectionCallbacks {
  private static final int LOCATION_REQUEST_ID = 1;
  private static final String[] REQUIRED_PERMISSIONS = {
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE
  };
  private static final int NUMBER_OF_FRAGMENTS = 8;
  private static final int FRAGMENT_INDEX_SETTING = 0;
  private static final int FRAGMENT_INDEX_LOGGER = 1;
  //private static final int FRAGMENT_INDEX_LOGGER_LOCATION = 2;
  //private static final int FRAGMENT_INDEX_LOGGER_MEASUREMENT = 3;
  //private static final int FRAGMENT_INDEX_LOGGER_NAVIGATION = 4;
  /*private static final int FRAGMENT_INDEX_LOGGER_GNSSSTATUS = 5;
  private static final int FRAGMENT_INDEX_LOGGER_NMEA = 6;*/
  private static final int FRAGMENT_INDEX_POSITION = 2;
  private static final int FRAGMENT_INDEX_PSEUDOLITE = 3;
  private static final int FRAGMENT_INDEX_PLOT = 4;
  private static final int FRAGMENT_INDEX_MAP = 5;
  private static final int FRAGMENT_INDEX_PLOT_PSEUDOLITE = 6;
  private static final int FRAGMENT_INDEX_SIMULATION = 7;
  private static final String TAG = "MainActivity";

  private GnssContainer mGnssContainer;
  private UiLogger mUiLogger;
/*  private UiLoggerLocation mUiLoggerLocation;
  private UiLoggerMeasurement mUiLoggerMeasurement;
  private UiLoggerNavigation mUiLoggerNavigation;
  private UiLoggerGnssStatus mUiLoggerGnssStatus;
  private UiLoggerNmea mUiLoggerNmea;*/
  //private RealTimePositionVelocityCalculator mRealTimePositionVelocityCalculator;
  private PositionCalculator mPositionCalculator;
  private PseudolitePositionCalculator mPseudolitePositionCalculator;
  private SimulationCalculator mSimulationCalculator;
  private FileLogger mFileLogger;
  private FileLoggerPseudolite mFileLoggerPseudolite;
  private FileLoggerSimulation mFileLoggerSimulation;
  // for test
  /*private TestCalculator mTestCalculator;
  private FileLoggerPseudolite mFileTest;*/

  private Fragment[] mFragments;
  private GoogleApiClient mGoogleApiClient;
  /*private boolean mAutoSwitchGroundTruthMode;
  private final ActivityDetectionBroadcastReceiver mBroadcastReceiver =
      new ActivityDetectionBroadcastReceiver();*/

  @Override
  protected void onStart() {
    super.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    /*LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            mBroadcastReceiver, new IntentFilter(
                DetectedActivitiesIntentReceiver.AR_RESULT_BROADCAST_ACTION));*/
  }

  @Override
  protected void onPause() {
    //LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    super.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy(){
    mGnssContainer.unregisterAll();
    super.onDestroy();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    SharedPreferences sharedPreferences = PreferenceManager.
        getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(SettingsFragment.PREFERENCE_KEY_AUTO_SCROLL, false);
    editor.commit();
    super.onCreate(savedInstanceState);
    /*//在使用SDK各组件之前初始化context信息，传入ApplicationContext
    //注意该方法要再setContentView方法之前实现
    SDKInitializer.initialize(getApplicationContext());*/
    setContentView(R.layout.activity_main);
    buildGoogleApiClient();
    requestPermissionAndSetupFragments(this);
  }

  protected PendingIntent createActivityDetectionPendingIntent() {
    Intent intent = new Intent(this, DetectedActivitiesIntentReceiver.class);
    return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private synchronized void buildGoogleApiClient() {
    mGoogleApiClient =
        new GoogleApiClient.Builder(this)
            .enableAutoManage(this, this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(ActivityRecognition.API)
            .build();
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    if (Log.isLoggable(TAG, Log.INFO)){
      Log.i(TAG,  "Connection failed: ErrorCode = " + result.getErrorCode());
    }
  }

  @Override
  public void onConnected(Bundle connectionHint) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Connected to GoogleApiClient");
    }
    ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
        mGoogleApiClient, 0, createActivityDetectionPendingIntent());
  }

  @Override
  public void onConnectionSuspended(int cause) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Connection suspended");
    }
  }

  /**
   * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the
   * sections/tabs/pages.
   */
  public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    public ViewPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      switch (position) {
        case FRAGMENT_INDEX_SETTING:
          return mFragments[FRAGMENT_INDEX_SETTING];
        case FRAGMENT_INDEX_LOGGER:
          return mFragments[FRAGMENT_INDEX_LOGGER];
        /*case FRAGMENT_INDEX_LOGGER_LOCATION:
          return mFragments[FRAGMENT_INDEX_LOGGER_LOCATION];
        case FRAGMENT_INDEX_LOGGER_MEASUREMENT:
          return mFragments[FRAGMENT_INDEX_LOGGER_MEASUREMENT];
        case FRAGMENT_INDEX_LOGGER_NAVIGATION:
          return mFragments[FRAGMENT_INDEX_LOGGER_NAVIGATION];*/
        /*case FRAGMENT_INDEX_LOGGER_GNSSSTATUS:
          return mFragments[FRAGMENT_INDEX_LOGGER_GNSSSTATUS];
        case FRAGMENT_INDEX_LOGGER_NMEA:
          return mFragments[FRAGMENT_INDEX_LOGGER_NMEA];*/
        case FRAGMENT_INDEX_POSITION:
          return mFragments[FRAGMENT_INDEX_POSITION];
        case FRAGMENT_INDEX_PSEUDOLITE:
          return mFragments[FRAGMENT_INDEX_PSEUDOLITE];
        case FRAGMENT_INDEX_PLOT:
          return mFragments[FRAGMENT_INDEX_PLOT];
        case FRAGMENT_INDEX_MAP:
          return mFragments[FRAGMENT_INDEX_MAP];
        case FRAGMENT_INDEX_PLOT_PSEUDOLITE:
          return mFragments[FRAGMENT_INDEX_PLOT_PSEUDOLITE];
        case FRAGMENT_INDEX_SIMULATION:
          return mFragments[FRAGMENT_INDEX_SIMULATION];
        /*case FRAGMENT_INDEX_TEST:
          return mFragments[FRAGMENT_INDEX_TEST];*/
        default:
          throw new IllegalArgumentException("Invalid section: " + position);
      }
    }

    @Override
    public int getCount() {
      // Show total pages.
      return NUMBER_OF_FRAGMENTS;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      Locale locale = Locale.getDefault();
      switch (position) {
        case FRAGMENT_INDEX_SETTING:
          return getString(R.string.title_settings).toUpperCase(locale);
        case FRAGMENT_INDEX_LOGGER:
          return getString(R.string.title_log).toUpperCase(locale);
        /*case FRAGMENT_INDEX_LOGGER_LOCATION:
          return getString(R.string.title_log_location).toUpperCase(locale);
        case FRAGMENT_INDEX_LOGGER_MEASUREMENT:
          return getString(R.string.title_log_measurement).toUpperCase(locale);
        case FRAGMENT_INDEX_LOGGER_NAVIGATION:
          return getString(R.string.title_log_navigation).toUpperCase(locale);*/
        /*case FRAGMENT_INDEX_LOGGER_GNSSSTATUS:
          return getString(R.string.title_log_gnssStatus).toUpperCase(locale);
        case FRAGMENT_INDEX_LOGGER_NMEA:
          return getString(R.string.title_log_nmea).toUpperCase(locale);*/
        case FRAGMENT_INDEX_POSITION:
          return getString(R.string.title_position).toUpperCase(locale);
        /*case FRAGMENT_INDEX_RESULT:
          return getString(R.string.title_offset).toUpperCase(locale);*/
        case FRAGMENT_INDEX_PSEUDOLITE:
          return getString(R.string.title_pseudolite).toUpperCase(locale);
        case FRAGMENT_INDEX_PLOT:
          return getString(R.string.title_plot).toUpperCase(locale);
        case FRAGMENT_INDEX_MAP:
          return getString(R.string.title_map).toUpperCase(locale);
        case FRAGMENT_INDEX_PLOT_PSEUDOLITE:
          return getString(R.string.title_plot_pseudolite).toUpperCase(locale);
        case FRAGMENT_INDEX_SIMULATION:
          return getString(R.string.title_simulation).toUpperCase(locale);
        /*case FRAGMENT_INDEX_TEST:
          return getString(R.string.title_test).toUpperCase(locale);*/
        default:
          return super.getPageTitle(position);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == LOCATION_REQUEST_ID) {
      // If request is cancelled, the result arrays are empty.
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        setupFragments();
      }
    }
  }

  private void setupFragments() {
    mUiLogger = new UiLogger();
    /*mUiLoggerLocation = new UiLoggerLocation();
    mUiLoggerMeasurement = new UiLoggerMeasurement();
    mUiLoggerNavigation = new UiLoggerNavigation();*/
    /*mUiLoggerGnssStatus = new UiLoggerGnssStatus();
    mUiLoggerNmea = new UiLoggerNmea();*/

    /*mRealTimePositionVelocityCalculator = new RealTimePositionVelocityCalculator();
    mRealTimePositionVelocityCalculator.setMainActivity(this);
    mRealTimePositionVelocityCalculator.setResidualPlotMode(
        RealTimePositionVelocityCalculator.RESIDUAL_MODE_DISABLED, null *//* fixedGroundTruth *//*);*/

    mPositionCalculator = new PositionCalculator();
    mPositionCalculator.setMainActivity(this);

    mFileLoggerPseudolite = new FileLoggerPseudolite(getApplicationContext());

    mPseudolitePositionCalculator = new PseudolitePositionCalculator();
    mPseudolitePositionCalculator.setMainActivity(this);
    mPseudolitePositionCalculator.setFileLoggerPseudolite(mFileLoggerPseudolite);

    mFileLoggerSimulation = new FileLoggerSimulation(getApplicationContext());

    mSimulationCalculator = new SimulationCalculator(getApplicationContext());
    mSimulationCalculator.setMainActivity(this);
    mSimulationCalculator.setFileLoggerSimulation(mFileLoggerSimulation);

    //for test
    /*mFileTest = new FileLoggerPseudolite(getApplicationContext());
    mTestCalculator = new TestCalculator();
    mTestCalculator.setMainActivity(this);
    mTestCalculator.setFileLoggerPseudolite(mFileTest);*/


    mFileLogger = new FileLogger(getApplicationContext());
    mGnssContainer =
        new GnssContainer(
            getApplicationContext(),
            mUiLogger,
            //mUiLoggerLocation,
            //mUiLoggerMeasurement,
            //mUiLoggerNavigation,
            //mUiLoggerGnssStatus,
            //mUiLoggerNmea,
            mFileLogger,
            mPositionCalculator,
            mPseudolitePositionCalculator
            //mTestCalculator,
            //mRealTimePositionVelocityCalculator
            );
    mGnssContainer.setSimulationCalculator(mSimulationCalculator);

    mFragments = new Fragment[NUMBER_OF_FRAGMENTS];
    SettingsFragment settingsFragment = new SettingsFragment();
    settingsFragment.setGpsContainer(mGnssContainer);
    //settingsFragment.setRealTimePositionVelocityCalculator(mRealTimePositionVelocityCalculator);
    //settingsFragment.setAutoModeSwitcher(this);
    mFragments[FRAGMENT_INDEX_SETTING] = settingsFragment;

    LoggerFragment loggerFragment = new LoggerFragment();
    loggerFragment.setUILogger(mUiLogger);
    loggerFragment.setFileLogger(mFileLogger);
    mFragments[FRAGMENT_INDEX_LOGGER] = loggerFragment;

    /*LoggerOneFragment loggerLocationFragment = new LoggerOneFragment();
    loggerLocationFragment.setUiLogger(mUiLoggerLocation);
    mFragments[FRAGMENT_INDEX_LOGGER_LOCATION] = loggerLocationFragment;

    LoggerOneFragment loggerMeasurementFragment = new LoggerOneFragment();
    loggerMeasurementFragment.setUiLogger(mUiLoggerMeasurement);
    mFragments[FRAGMENT_INDEX_LOGGER_MEASUREMENT] = loggerMeasurementFragment;

    LoggerOneFragment loggerNavigationFragment = new LoggerOneFragment();
    loggerNavigationFragment.setUiLogger(mUiLoggerNavigation);
    mFragments[FRAGMENT_INDEX_LOGGER_NAVIGATION] = loggerNavigationFragment;*/

    /*LoggerOneFragment loggerGnssStatusFragment = new LoggerOneFragment();
    loggerGnssStatusFragment.setUiLogger(mUiLoggerGnssStatus);
    mFragments[FRAGMENT_INDEX_LOGGER_GNSSSTATUS] = loggerGnssStatusFragment;

    LoggerOneFragment loggerNmeaFragment = new LoggerOneFragment();
    loggerNmeaFragment.setUiLogger(mUiLoggerNmea);
    mFragments[FRAGMENT_INDEX_LOGGER_NMEA] = loggerNmeaFragment;*/

    ResultFragment positionFragment = new ResultFragment();
    positionFragment.setPositionCalculator(mPositionCalculator);
    mFragments[FRAGMENT_INDEX_POSITION] = positionFragment;

    /*ResultFragment resultFragment = new ResultFragment();
    resultFragment.setPositionVelocityCalculator(mRealTimePositionVelocityCalculator);
    mFragments[FRAGMENT_INDEX_RESULT] = resultFragment;*/

    PseudoliteFragment pseudoliteFragment = new PseudoliteFragment();
    pseudoliteFragment.setPseudolitePositionCalculator(mPseudolitePositionCalculator);
    pseudoliteFragment.setFileLoggerPseudolite(mFileLoggerPseudolite);
    mFragments[FRAGMENT_INDEX_PSEUDOLITE] = pseudoliteFragment;

    PlotFragment plotFragment = new PlotFragment();
    mFragments[FRAGMENT_INDEX_PLOT] = plotFragment;
    mPositionCalculator.setPlotFragment(plotFragment);

    BaiduMapFragment mapFragment = new BaiduMapFragment();
    mFragments[FRAGMENT_INDEX_MAP] = mapFragment;
    mPositionCalculator.setBaiduMapFragment(mapFragment);

    PlotPseudoliteFragment mPlotPseudoliteFragment = new PlotPseudoliteFragment();
    mFragments[FRAGMENT_INDEX_PLOT_PSEUDOLITE] = mPlotPseudoliteFragment;
    mPseudolitePositionCalculator.setPlotPseudoliteFragment(mPlotPseudoliteFragment);

    SimulationFragment simulationFragment = new SimulationFragment();
    mSimulationCalculator.setPlotSimulationFragment(mPlotPseudoliteFragment);
    simulationFragment.setSimulationCalculator(mSimulationCalculator);
    simulationFragment.setmFileLoggerSimulation(mFileLoggerSimulation);
    mFragments[FRAGMENT_INDEX_SIMULATION] = simulationFragment;

    //for test
    /*TestFragment testFragment = new TestFragment();
    testFragment.setTestCalculator(mTestCalculator);
    testFragment.setFileLoggerPseudolite(mFileTest);
    mFragments[FRAGMENT_INDEX_TEST] = testFragment;*/


    // The viewpager that will host the section contents.
    ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
    viewPager.setOffscreenPageLimit(NUMBER_OF_FRAGMENTS);
    ViewPagerAdapter adapter = new ViewPagerAdapter(getFragmentManager());
    viewPager.setAdapter(adapter);

    TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
    tabLayout.setTabsFromPagerAdapter(adapter);

    // Set a listener via setOnTabSelectedListener(OnTabSelectedListener) to be notified when any
    // tab's selection state has been changed.
    tabLayout.setOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));

    // Use a TabLayout.TabLayoutOnPageChangeListener to forward the scroll and selection changes to
    // this layout
    viewPager.addOnPageChangeListener(new TabLayoutOnPageChangeListener(tabLayout));
  }

  private boolean hasPermissions(Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.M) {
      // Permissions granted at install time.
      return true;
    }
    for (String p : REQUIRED_PERMISSIONS) {
      if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private void requestPermissionAndSetupFragments(final Activity activity) {
    if (hasPermissions(activity)) {
      setupFragments();
    } else {
      ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, LOCATION_REQUEST_ID);
    }
  }
}
