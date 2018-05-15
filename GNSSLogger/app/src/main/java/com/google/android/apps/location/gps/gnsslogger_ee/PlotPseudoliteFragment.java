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

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.os.Bundle;
import android.support.v4.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.location.lbs.gnss.gps.pseudorange.GpsNavigationMessageStore;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.util.MathHelper;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** A plot fragment to show real-time data (only from GPS). */
public class PlotPseudoliteFragment extends Fragment {

  /** Total number of kinds of plot tabs */
  private static final int NUMBER_OF_TABS = 9;

  /** The position of the raw pseudorange over time plot tab */
  private static final int RAW_PSEUDORANGE_TAB = 0;

  /** The position of the pseudorange of the outdoor antenna to satellite over time plot tab*/
  private static final int ANTENNA_TO_SAT_PSEUDORANGE_TAB = 1;

  /** The position of the pseudorange of the indoor antenna to user over time plot tab*/
  private static final int ANTENNA_TO_USER_PSEUDORANGE_TAB = 2;

  /** The position of the change of raw pseudorange over time plot tab */
  private static final int CHANGE_RAW_PSEUDORANGE_TAB = 3;

  /** The position of the change of pseudorange of the outdoor antenna to satellite over time plot tab*/
  private static final int CHANGE_ANTENNA_TO_SAT_PSEUDORANGE_TAB = 4;

  /** The position of the change of pseudorange of the indoor antenna to user over time plot tab*/
  private static final int CHANGE_ANTENNA_TO_USER_PSEUDORANGE_TAB = 5;

  /** The position of the raw pseudorange rate over time plot tab */
  private static final int RAW_PSEUDORANGE_RATE_TAB = 6;

  /** The position of the pseudorange rate of the outdoor antenna to satellite over time plot tab*/
  private static final int ANTENNA_TO_SAT_PSEUDORANGE_RATE_TAB = 7;

  /** The position of the pseudorange rate of the indoor antenna to user over time plot tab*/
  private static final int ANTENNA_TO_USER_PSEUDORANGE_RATE_TAB = 8;

  /** The X range of the plot, we are keeping the latest one minute visible */
  private static final double TIME_INTERVAL_SECONDS = 60;

  /** Data format used to format the data in the text view */
  private static final DecimalFormat sDataFormat =
      new DecimalFormat("##.#", new DecimalFormatSymbols(Locale.US));

  private GraphicalView mChartView;

  private double mInitialTimeSeconds = -1;
  private double mLastTimeReceivedSeconds = 0;
  private final ColorMap mColorMap = new ColorMap();
  private DataSetManager mDataSetManager;
  private XYMultipleSeriesRenderer mCurrentRenderer;
  private LinearLayout mLayout;
  private int mCurrentTab = 0;
  private double[][] minYAndMaxY = new double[NUMBER_OF_TABS][2];

  private Spinner tabSpinner;


  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View plotView = inflater.inflate(R.layout.fragment_plot_pseudolite, container, false /* attachToRoot */);

    for (int i = 0; i < NUMBER_OF_TABS; ++i) {
      minYAndMaxY[i][0] = Double.MAX_VALUE;
      minYAndMaxY[i][1] = Double.MIN_VALUE;
    }

    mDataSetManager
        = new DataSetManager(NUMBER_OF_TABS, getContext(), mColorMap);

    // Set UI elements handlers
    tabSpinner = plotView.findViewById(R.id.tab_spinner);

    OnItemSelectedListener spinnerOnSelectedListener = new OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mCurrentTab = tabSpinner.getSelectedItemPosition();
        XYMultipleSeriesRenderer renderer
            = mDataSetManager.getRenderer(mCurrentTab);
        XYMultipleSeriesDataset dataSet
            = mDataSetManager.getDataSet(mCurrentTab);
        if (mLastTimeReceivedSeconds > TIME_INTERVAL_SECONDS) {
          renderer.setXAxisMax(mLastTimeReceivedSeconds);
          renderer.setXAxisMin(mLastTimeReceivedSeconds - TIME_INTERVAL_SECONDS);
        }
        mCurrentRenderer = renderer;
        mLayout.removeAllViews();
        mChartView = ChartFactory.getLineChartView(getContext(), dataSet, renderer);
        mLayout.addView(mChartView);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    };

    tabSpinner.setOnItemSelectedListener(spinnerOnSelectedListener);

    // Set up the Graph View
    mCurrentRenderer = mDataSetManager.getRenderer(mCurrentTab);
    XYMultipleSeriesDataset currentDataSet
        = mDataSetManager.getDataSet(mCurrentTab);
    mChartView = ChartFactory.getLineChartView(getContext(), currentDataSet, mCurrentRenderer);
    mLayout = plotView.findViewById(R.id.plot);
    mLayout.addView(mChartView);
    return plotView;
  }

  /**
   * Updates the plot
   *
   * @param data An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with corresponding data
   * @param timeInSeconds the time at which measurements are received
   * @param index the index of tabSpinner
   */
  protected void updatePseudorangeRelatedTab(double[] data, double timeInSeconds, int index) {
    if (mInitialTimeSeconds < 0) {
      mInitialTimeSeconds = timeInSeconds;
    }
    mLastTimeReceivedSeconds = timeInSeconds - mInitialTimeSeconds;
    for (int i = 1; i <= GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (!Double.isNaN(data[i - 1])) {
        mDataSetManager.addValue(
            index,
            i,
            mLastTimeReceivedSeconds,
            data[i - 1]);
        if (data[i - 1] < minYAndMaxY[index][0]) {
          minYAndMaxY[index][0] = data[i - 1];
        }
        if (data[i - 1] > minYAndMaxY[index][1]) {
          minYAndMaxY[index][1] = data[i - 1];
        }
      }
    }
    mDataSetManager.fillInDiscontinuity(index, mLastTimeReceivedSeconds);

    mCurrentRenderer = mDataSetManager.getRenderer(index);
    mCurrentRenderer.setYAxisMin(minYAndMaxY[index][0]);
    mCurrentRenderer.setYAxisMax(minYAndMaxY[index][1]);

    // Checks if the plot has reached the end of frame and resize
    if (mLastTimeReceivedSeconds > mCurrentRenderer.getXAxisMax()) {
      mCurrentRenderer.setXAxisMax(mLastTimeReceivedSeconds);
      mCurrentRenderer.setXAxisMin(mLastTimeReceivedSeconds - TIME_INTERVAL_SECONDS);
    }

    mChartView.invalidate();
  }

  /**
   * Updates the raw pseudorange plot
   *
   * @param rawPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with raw pseudorange in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateRawPseudorangesTab(double[] rawPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(rawPseudoranges, timeInSeconds, RAW_PSEUDORANGE_TAB);
  }

  /**
   * Updates the pseudorange from the outdoor antenna to satellites
   *
   * @param antennaToSatPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with pseudorange from itself to the outdoor antenna in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateAntennaToSatPseudorangesTab(double[] antennaToSatPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(antennaToSatPseudoranges, timeInSeconds, ANTENNA_TO_SAT_PSEUDORANGE_TAB);
  }

  /**
   * Updates the pseudorange from the indoor antenna to user
   *
   * @param antennaToUserPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with pseudorange from indoor antenna to user in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateAntennaToUserPseudorangesTab(double[] antennaToUserPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(antennaToUserPseudoranges, timeInSeconds, ANTENNA_TO_USER_PSEUDORANGE_TAB);
  }

  /**
   * Updates the change of raw pseudorange plot
   *
   * @param changeOfRawPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with change of raw pseudorange in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateChangeOfRawPseudorangesTab(double[] changeOfRawPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(changeOfRawPseudoranges, timeInSeconds, CHANGE_RAW_PSEUDORANGE_TAB);
  }

  /**
   * Updates the change of pseudorange from the outdoor antenna to satellites
   *
   * @param changeOfAntennaToSatPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with change of pseudorange from itself to the outdoor antenna in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateChangeOfAntennaToSatPseudorangesTab(double[] changeOfAntennaToSatPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(changeOfAntennaToSatPseudoranges, timeInSeconds, CHANGE_ANTENNA_TO_SAT_PSEUDORANGE_TAB);
  }

  /**
   * Updates the change of pseudorange from the indoor antenna to user
   *
   * @param changeOfAntennaToUserPseudoranges An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with change of pseudorange from indoor antenna to user in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateChangeOfAntennaToUserPseudorangesTab(double[] changeOfAntennaToUserPseudoranges, double timeInSeconds) {
    updatePseudorangeRelatedTab(changeOfAntennaToUserPseudoranges, timeInSeconds, CHANGE_ANTENNA_TO_USER_PSEUDORANGE_TAB);
  }

  /**
   * Updates the raw pseudorange rate plot
   *
   * @param rawPseudorangesRate An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with raw pseudorange rate in m/s
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateRawPseudorangesRateTab(double[] rawPseudorangesRate, double timeInSeconds) {
    updatePseudorangeRelatedTab(rawPseudorangesRate, timeInSeconds, RAW_PSEUDORANGE_RATE_TAB);
  }

  /**
   * Updates the pseudorange rate from the outdoor antenna to satellites
   *
   * @param antennaToSatPseudorangesRate An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with pseudorange rate from itself to the outdoor antenna in m/s
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateAntennaToSatPseudorangesRateTab(double[] antennaToSatPseudorangesRate, double timeInSeconds) {
    updatePseudorangeRelatedTab(antennaToSatPseudorangesRate, timeInSeconds, ANTENNA_TO_SAT_PSEUDORANGE_RATE_TAB);
  }

  /**
   * Updates the pseudorange rate from the indoor antenna to user
   *
   * @param antennaToUserPseudorangesRate An array of MAX_NUMBER_OF_SATELLITES elements where indexes of satellites was
   *        not seen are fixed with {@code Double.NaN} and indexes of satellites what were seen
   *        are filled with pseudorange rate from indoor antenna to user in meters
   * @param timeInSeconds the time at which measurements are received
   */
  protected void updateAntennaToUserPseudorangesRateTab(double[] antennaToUserPseudorangesRate, double timeInSeconds) {
    updatePseudorangeRelatedTab(antennaToUserPseudorangesRate, timeInSeconds, ANTENNA_TO_USER_PSEUDORANGE_RATE_TAB);
  }

  /**
   * An utility class provides and keeps record of all color assignments to the satellite in the
   * plots. Each satellite will receive a unique color assignment through out every graph.
   */
  private static class ColorMap {

    private ArrayMap<Integer, Integer> mColorMap = new ArrayMap<>();
    private int mColorsAssigned = 0;
    /**
     * Source of Kelly's contrasting colors:
     * https://medium.com/@rjurney/kellys-22-colours-of-maximum-contrast-58edb70c90d1
     */
    private static final String[] CONTRASTING_COLORS = {
      "#222222", "#F3C300", "#875692", "#F38400", "#A1CAF1", "#BE0032", "#C2B280", "#848482",
      "#008856", "#E68FAC", "#0067A5", "#F99379", "#604E97", "#F6A600", "#B3446C", "#DCD300",
      "#882D17", "#8DB600", "#654522", "#E25822", "#2B3D26"
    };
    private final Random mRandom = new Random();

    private int getColor(int svId) {
      // Assign the color from Kelly's 21 contrasting colors to satellites first, if all color
      // has been assigned, use a random color and record in {@link mColorMap}.
      if (mColorMap.containsKey(svId)) {
        return mColorMap.get(getUniqueSatelliteIdentifier(svId));
      }
      if (this.mColorsAssigned < CONTRASTING_COLORS.length) {
        int color = Color.parseColor(CONTRASTING_COLORS[mColorsAssigned++]);
        mColorMap.put(getUniqueSatelliteIdentifier(svId), color);
        return color;
      }
      int color = Color.argb(255, mRandom.nextInt(256), mRandom.nextInt(256), mRandom.nextInt(256));
      mColorMap.put(getUniqueSatelliteIdentifier(svId), color);
      return color;
    }
  }

  private static int getUniqueSatelliteIdentifier(int svID){
    return svID;
  }

  /**
   * An utility class stores and maintains all the data sets and corresponding renders.
   */
  private static class DataSetManager {
    private final ArrayMap<Integer, Integer>[] mSatelliteIndex;
    //private final ArrayMap<Integer, Integer>[] mSatelliteConstellationIndex;
    private final XYMultipleSeriesDataset[] mDataSetList;
    private final XYMultipleSeriesRenderer[] mRendererList;
    private final Context mContext;
    private final ColorMap mColorMap;

    public DataSetManager(int numberOfTabs, Context context, ColorMap colorMap) {
      mDataSetList = new XYMultipleSeriesDataset[numberOfTabs];
      mRendererList = new XYMultipleSeriesRenderer[numberOfTabs];
      mSatelliteIndex = new ArrayMap[numberOfTabs];
      //mSatelliteConstellationIndex = new ArrayMap[numberOfTabs];
      mContext = context;
      mColorMap = colorMap;

      // Preparing data sets and renderer
      for (int i = 0; i < numberOfTabs; i++) {
        mSatelliteIndex[i] = new ArrayMap<>();
        //mSatelliteConstellationIndex[i] = new ArrayMap<>();
        XYMultipleSeriesRenderer tempRenderer = new XYMultipleSeriesRenderer();
        setUpRenderer(tempRenderer, i);
        mRendererList[i] = tempRenderer;
        XYMultipleSeriesDataset tempDataSet = new XYMultipleSeriesDataset();
        mDataSetList[i] = tempDataSet;
      }
    }

    /**
     * Returns the multiple series data set at specific tab and index
     */
    private XYMultipleSeriesDataset getDataSet(int tab) {
      return mDataSetList[tab];
    }

    /**
     * Returns the multiple series renderer set at specific tab and index
     */
    private XYMultipleSeriesRenderer getRenderer(int tab) {
      return mRendererList[tab];
    }

    /**
     * Adds a value into the both the data set containing all constellations and individual data set
     * of the constellation of the satellite
     */
    private void addValue(int tab, int svID, double timeInSeconds, double value) {
      value = Double.parseDouble(sDataFormat.format(value));
      if (hasSeen(svID, tab)) {
        // If the satellite has been seen before, we retrieve the dataseries it is add and add new
        // data
        mDataSetList[tab]
            .getSeriesAt(mSatelliteIndex[tab].get(svID))
            .add(timeInSeconds, value);
      } else {
        // If the satellite has not been seen before, we create new dataset and renderer before
        // adding data
        mSatelliteIndex[tab]
            .put(svID, mDataSetList[tab].getSeriesCount());
        XYSeries tempSeries = new XYSeries("GPS" + svID);
        tempSeries.add(timeInSeconds, value);
        mDataSetList[tab].addSeries(tempSeries);
        XYSeriesRenderer tempRenderer = new XYSeriesRenderer();
        tempRenderer.setLineWidth(5);
        tempRenderer.setColor(mColorMap.getColor(svID));
        mRendererList[tab].addSeriesRenderer(tempRenderer);
      }
    }

    /**
     * Creates a discontinuity of the satellites that has been seen but not reported in this batch
     * of measurements
     */
    private void fillInDiscontinuity(int tab, double referenceTimeSeconds) {
      XYMultipleSeriesDataset dataSet = mDataSetList[tab];
      for (int i = 0; i < dataSet.getSeriesCount(); i++) {
        if (dataSet.getSeriesAt(i).getMaxX() < referenceTimeSeconds) {
          dataSet.getSeriesAt(i).add(referenceTimeSeconds, MathHelper.NULL_VALUE);
        }
      }
    }

    /**
     * Returns a boolean indicating whether the input satellite has been seen.
     */
    private boolean hasSeen(int svID, int tab) {
      return mSatelliteIndex[tab].containsKey(svID);
    }

    /**
     * Set up a {@link XYMultipleSeriesRenderer} with the specs customized per plot tab.
     */
    private void setUpRenderer(XYMultipleSeriesRenderer renderer, int tabNumber) {
      renderer.setXAxisMin(0);
      renderer.setXAxisMax(60);
      renderer.setYAxisAlign(Align.RIGHT, 0);
      renderer.setLegendTextSize(30);
      renderer.setLabelsTextSize(30);
      renderer.setYLabelsColor(0, Color.BLACK);
      renderer.setXLabelsColor(Color.BLACK);
      renderer.setFitLegend(true);
      renderer.setShowGridX(true);
      renderer.setMargins(new int[] {10, 10, 30, 10});
      // setting the plot untouchable
      renderer.setZoomEnabled(false, false);
      renderer.setPanEnabled(false, true);
      renderer.setClickEnabled(false);
      renderer.setMarginsColor(Color.WHITE);
      renderer.setChartTitle(mContext.getResources()
          .getStringArray(R.array.plot_pseudolite_titles)[tabNumber]);
      renderer.setChartTitleTextSize(50);
    }
  }
}
