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

package com.google.location.lbs.gnss.gps.pseudorange;

import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.location.lbs.gnss.gps.pseudorange.Ecef2LlaConverter.GeodeticLlaValues;
import com.google.location.lbs.gnss.gps.pseudorange.EcefToTopocentricConverter.TopocentricAEDValues;
import com.google.location.lbs.gnss.gps.pseudorange.SatellitePositionCalculator.PositionAndVelocity;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Computes an iterative least square receiver position solution given the pseudorange (meters) and
 * accumulated delta range (meters) measurements, receiver time of week, week number and the
 * navigation message.
 */
class PseudolitePositioningLeastSquare {
  private static final double SPEED_OF_LIGHT_MPS = 299792458.0;
  private static final int SECONDS_IN_WEEK = 604800;
  private static final double LEAST_SQUARE_TOLERANCE_METERS = 4.0e-8;
  private static final double LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS = 1.0e-4;
  /** Position correction threshold below which atmospheric correction will be applied */
  private static final double ATMPOSPHERIC_CORRECTIONS_THRESHOLD_METERS = 1000.0;
  private static final int MINIMUM_NUMER_OF_SATELLITES = 4;
  private static final double RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS = 20.0;
  private static final int MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS = 100;
  /** GPS C/A code chip width Tc = 1 microseconds */
  private static final double GPS_CHIP_WIDTH_T_C_SEC = 1.0e-6;
  /** Narrow correlator with spacing d = 0.1 chip */
  private static final double GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1;
  /** Average time of DLL correlator T of 20 milliseconds */
  private static final double GPS_DLL_AVERAGING_TIME_SEC = 20.0e-3;
  /** Average signal travel time from GPS satellite and earth */
  private static final double AVERAGE_TRAVEL_TIME_SECONDS = 70.0e-3;
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private static final double DOUBLE_ROUND_OFF_TOLERANCE = 0.0000000001;

  private static final int MAX_NUM_OF_ITERATION = 100;

  private final PseudorangeSmoother pseudorangeSmoother;
  private double geoidHeightMeters;
  private ElevationApiHelper elevationApiHelper;
  private boolean calculateGeoidMeters = true;
  private RealMatrix geometryMatrix;

  // 室外接收天线到卫星伪距
  private double[] mAntennaToSatPseudorangesMeters =
      GpsMathOperations.createAndFillArray(
          GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES, Double.NaN
      );
  // 室内转发天线到用户伪距
  private double[] mAntennaToUserPseudorangesMeters =
      GpsMathOperations.createAndFillArray(
          GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES, Double.NaN
      );

  /** Constructor */
  public PseudolitePositioningLeastSquare(PseudorangeSmoother pseudorangeSmoother) {
    this.pseudorangeSmoother = pseudorangeSmoother;
  }

  /** Constructor with Google Elevation API Key */
  public PseudolitePositioningLeastSquare(PseudorangeSmoother pseudorangeSmoother,
                                          String elevationApiKey){
    this.pseudorangeSmoother = pseudorangeSmoother;
    this.elevationApiHelper = new ElevationApiHelper(elevationApiKey);
  }

  /**
   * Least square solution to calculate the pseudolite positioning result
   * First we calculate the pseu-position of the user in order to get the
   * position of the satellites, then calculate the true position of user
   * given the information of pseudolites
   * 用最小二乘计算伪卫星定位结果，首先假设没有伪卫星的存在，照常进行定位，以得到
   * 卫星位置，然后再利用伪卫星的信息计算得到用户真正的位置
   **/
  public void calculatePseudolitePositioningLeastSquare(
      PseudoliteMessageStore pseudoliteMessageStore,
      GpsNavMessageProto navMessageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGPSWeek,
      int dayOfYear1To366,
      double[] positionSolution)
      throws Exception {
    double[] positionSolutionECEF = GpsMathOperations.createAndFillArray(4, 0);
    // 先假设不存在伪卫星，直接进行定位以得到卫星位置
    SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResAndCovMatrix =
        calculateUserPositionLeastSquare(navMessageProto, usefulSatellitesToReceiverMeasurements,
            receiverGPSTowAtReceptionSeconds, receiverGPSWeek, dayOfYear1To366, positionSolutionECEF);
    double[][] satellitesPositionECEFMeters = satPosPseudorangeResAndCovMatrix.satellitesPositionsMeters;
    int[] satellitePRNs = satPosPseudorangeResAndCovMatrix.satellitePRNs;
    double[] outdoorAntennaLla = pseudoliteMessageStore.getOutdoorAntennaLla();
    double[] outdoorAntennaECEF = Lla2EcefConverter.convertFromLlaToEcefMeters(
        new GeodeticLlaValues(Math.toRadians(outdoorAntennaLla[0]),
            Math.toRadians(outdoorAntennaLla[1]), outdoorAntennaLla[2]));
    /*// 先用解出来的位置上方作为室外天线位置来测试
    double[] outdoorAntennaECEF = Lla2EcefConverter.convertFromLlaToEcefMeters(
        new GeodeticLlaValues(Math.toRadians(positionSolutionECEF[0]),
            Math.toRadians(positionSolutionECEF[1]), positionSolutionECEF[2] + 10));*/

    List<GpsMeasurementWithRangeAndUncertainty> immutableSmoothedSatellitesToReceiverMeasurements =
        pseudorangeSmoother.updatePseudorangeSmoothingResult(
            Collections.unmodifiableList(usefulSatellitesToReceiverMeasurements));
    List<GpsMeasurementWithRangeAndUncertainty> mutableSmoothedSatellitesToReceiverMeasurements =
        Lists.newArrayList(immutableSmoothedSatellitesToReceiverMeasurements);

    // Correct the receiver time of week with the estimated receiver clock bias
    //receiverGPSTowAtReceptionSeconds -= positionSolutionECEF[3] / SPEED_OF_LIGHT_MPS;

    double[][] indoorAntennasXyz = pseudoliteMessageStore.getIndoorAntennasXyz();
    int pseudoliteNum = indoorAntennasXyz.length;

    double[] outdoorToIndoorRange = pseudoliteMessageStore.getOutdoorToIndoorRange();

    double[] pseudoliteToUserRange = new double[pseudoliteNum];
    double minValue = Double.MAX_VALUE;

    for (int satsCounter = 0; satsCounter < pseudoliteNum; ++satsCounter) {
      GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, satellitePRNs[satsCounter]);
      // 卫星到用户的伪距
      double pseudorangeMeasurementMeters =
          mutableSmoothedSatellitesToReceiverMeasurements.get(satellitePRNs[satsCounter] - 1).pseudorangeMeters;
      GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
          calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
              receiverGPSWeek, pseudorangeMeasurementMeters);

      // Ionospheric model parameters
      double[] alpha =
          {navMessageProto.iono.alpha[0], navMessageProto.iono.alpha[1],
              navMessageProto.iono.alpha[2], navMessageProto.iono.alpha[3]};
      double[] beta = {navMessageProto.iono.beta[0], navMessageProto.iono.beta[1],
          navMessageProto.iono.beta[2], navMessageProto.iono.beta[3]};

      // Calculate ionospheric and tropospheric corrections
      double ionosphericCorrectionMeters;
      double troposphericCorrectionMeters;
      if (satPosPseudorangeResAndCovMatrix.doAtmosphericCorrections) {
        ionosphericCorrectionMeters =
            IonosphericModel.ionoKloboucharCorrectionSeconds(
                outdoorAntennaECEF,
                satellitesPositionECEFMeters[satsCounter],
                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                alpha,
                beta,
                IonosphericModel.L1_FREQ_HZ)
                * SPEED_OF_LIGHT_MPS;

        troposphericCorrectionMeters =
            calculateTroposphericCorrectionMeters(
                dayOfYear1To366,
                satellitesPositionECEFMeters,
                outdoorAntennaECEF,
                satsCounter);
      } else {
        troposphericCorrectionMeters = 0.0;
        ionosphericCorrectionMeters = 0.0;
      }

      double satelliteToOutdoorAntennaPseudorange = calculatePredictedPseudorange(positionSolutionECEF,
          satellitesPositionECEFMeters, outdoorAntennaECEF, satsCounter, ephemeridesProto,
          correctedTowAndWeek, ionosphericCorrectionMeters, troposphericCorrectionMeters);

      //for visualization
      mAntennaToSatPseudorangesMeters[satellitePRNs[satsCounter] - 1] = satelliteToOutdoorAntennaPseudorange;

      // 室内转发天线到用户距离
      pseudoliteToUserRange[satsCounter] = pseudorangeMeasurementMeters - satelliteToOutdoorAntennaPseudorange -
          outdoorToIndoorRange[satsCounter];

      // for visualization
      mAntennaToUserPseudorangesMeters[satellitePRNs[satsCounter] - 1] = pseudoliteToUserRange[satsCounter];

      if (pseudoliteToUserRange[satsCounter] < minValue) {
        minValue = pseudoliteToUserRange[satsCounter];
      }

      Log.d("伪卫星最小二乘", "pseudorangeMeasurementMeters:"+pseudorangeMeasurementMeters+
          " satelliteToOutdoorAntennaPseudorange:"+satelliteToOutdoorAntennaPseudorange
          +" 伪卫星到用户伪距"+satsCounter+":"+pseudoliteToUserRange[satsCounter]);

    }

    // 用最小二乘进行定位
    double bias = 0.0;
    double[] estiPseuoRange = new double[pseudoliteNum];
    double[] deltaPseuoRange = new double[pseudoliteNum];
    double[] deltaPosition;
    int calTimes = MAX_NUM_OF_ITERATION;
    double error = 10;

    double minPseuorange = Double.MAX_VALUE;
    for (int i = 0; i < pseudoliteNum; ++i) {
      double[] r = {indoorAntennasXyz[i][0] - positionSolution[0],
          indoorAntennasXyz[i][1] - positionSolution[1],
          indoorAntennasXyz[i][2] - positionSolution[2]};
      // 根据初始化的用户位置计算得到预计的伪卫星到用户的伪距
      estiPseuoRange[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + bias;
      if (estiPseuoRange[i] < minPseuorange) {
        minPseuorange = estiPseuoRange[i];
      }
      //deltaPseuoRange[i] = pseudoliteToUserRange[i] - estiPseuoRange[i];
      //Log.d("伪卫星最小二乘", "estiPseudoRange["+i+"]:"+estiPseuoRange[i]+
      //    " deltaPseuoRange["+i+"]:"+deltaPseuoRange[i]);
    }

    /*Random random = new Random();
    double[] simulationPos = {1.0, 1.0, 0.0};*/
    for (int satsCounter = 0; satsCounter < pseudoliteNum; ++satsCounter) {
      //pseudoliteToUserRange[satsCounter] -= (minValue - minPseuorange);

      // 仿真用，假设用户在（1，1，0），同时叠加高斯噪声
      /*double[] r = {indoorAntennasXyz[satsCounter][0] - simulationPos[0],
          indoorAntennasXyz[satsCounter][1] - simulationPos[1],
          indoorAntennasXyz[satsCounter][2] - simulationPos[2]};
      pseudoliteToUserRange[satsCounter] = Math.sqrt(Math.pow(r[0],2)+
          Math.pow(r[1],2)+Math.pow(r[2],2))+random.nextGaussian();*/

      // 伪距残差
      deltaPseuoRange[satsCounter] = pseudoliteToUserRange[satsCounter] - estiPseuoRange[satsCounter];
      Log.d("伪卫星最小二乘", "处理后的伪卫星到用户伪距"+satsCounter+":"+pseudoliteToUserRange[satsCounter]
          +" estiPseudoRange["+satsCounter+"]:"+estiPseuoRange[satsCounter]+
          " deltaPseuoRange["+satsCounter+"]:"+deltaPseuoRange[satsCounter]);
    }

    double delta1 = 0.0;
    double lambda = 1.0;
    for (double deltaPr : deltaPseuoRange) {
      delta1 += Math.pow(deltaPr, 2);
    }

    /*while (error >= LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS && calTimes > 0) {
      RealMatrix connectionMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          indoorAntennasXyz, positionSolution));
      double[][] eye = new double[4][4];
      for (int row = 0; row < 4; ++row) {
        for (int col = 0; col < 4; ++col) {
          if (row == col) {
            eye[row][col] = lambda;
          }
          else {
            eye[row][col] = 0;
          }
        }
      }

      RealMatrix GTG = connectionMatrix.transpose().multiply(connectionMatrix);
      RealMatrix temp = GTG.add(new Array2DRowRealMatrix(eye));
      RealMatrix hMatrix = new LUDecomposition(temp).getSolver().getInverse();
      RealMatrix buffConnectionMatrix = hMatrix.multiply(connectionMatrix.transpose());
      deltaPosition = GpsMathOperations.matrixByColVectMultiplication(buffConnectionMatrix.getData(),
          deltaPseuoRange);
      bias += deltaPosition[3];
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--bias="+bias);

      double[][] t = temp.getData();
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--temp="+t[0][0]+","
          +t[0][1]+","+t[0][2]+","+t[0][3]+"\n"+t[1][0]+","+t[1][1]+","+t[1][2]+","+t[1][3]+"\n"
          +t[2][0]+","+t[2][1]+","+t[2][2]+","+t[2][3]+"\n"+t[3][0]+","+t[3][1]+","+t[3][2]+","+t[3][3]);

      double tempdet = new LUDecomposition(temp).getDeterminant();
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--det(temp)="+tempdet);

      // Apply corrections to the position estimate
      positionSolution[0] += deltaPosition[0];
      positionSolution[1] += deltaPosition[1];
      positionSolution[2] += deltaPosition[2];
      positionSolution[3] += deltaPosition[3];

      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--deltaPosition="+deltaPosition[0]
      +","+deltaPosition[1]+","+deltaPosition[2]+","+deltaPosition[3]);

      for (int i = 0; i < pseudoliteNum; ++i) {
        double[] r = {indoorAntennasXyz[i][0] - positionSolution[0],
            indoorAntennasXyz[i][1] - positionSolution[1],
            indoorAntennasXyz[i][2] - positionSolution[2]};
        estiPseuoRange[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + bias;
        deltaPseuoRange[i] = pseudoliteToUserRange[i] - estiPseuoRange[i];
        Log.d("伪卫星最小二乘", "estiPseudoRange["+i+"]:"+estiPseuoRange[i]);
        Log.d("伪卫星最小二乘", "deltaPseuoRange["+i+"]:"+deltaPseuoRange[i]);
      }
      double delta2 = 0.0;
      for (double deltaPr : deltaPseuoRange) {
        delta2 += Math.pow(deltaPr, 2);
      }

      double norm = 0.0;
      for (double dp : deltaPosition) {
        norm += Math.pow(dp, 2);
      }
      norm = Math.sqrt(norm);

      Log.d("伪卫星最小二乘", "delta1:"+delta1);
      Log.d("伪卫星最小二乘", "delta2:"+delta2);
      Log.d("伪卫星最小二乘", "norm:"+norm);

      if (delta1 > delta2) {

        lambda = lambda / (1 + norm);
      } else {
        lambda = lambda + (delta2 - delta1) / (1 + norm);
      }
      delta1 = delta2;

      Log.d("伪卫星最小二乘", "lambda:"+lambda);

      error = norm;
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--error="+error);
      --calTimes;
    }*/

    lambda = 0.1;
    double miu = 10;
    while (error >= LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS && calTimes > 0) {
      RealMatrix connectionMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          indoorAntennasXyz, positionSolution));
      double[][] eye = new double[4][4];
      for (int row = 0; row < 4; ++row) {
        for (int col = 0; col < 4; ++col) {
          if (row == col) {
            eye[row][col] = lambda;
          } else {
            eye[row][col] = 0;
          }
        }
      }

      RealMatrix GTG = connectionMatrix.transpose().multiply(connectionMatrix);
      RealMatrix temp = GTG.add(new Array2DRowRealMatrix(eye));
      RealMatrix hMatrix = new LUDecomposition(temp).getSolver().getInverse();
      RealMatrix buffConnectionMatrix = hMatrix.multiply(connectionMatrix.transpose());
      deltaPosition = GpsMathOperations.matrixByColVectMultiplication(buffConnectionMatrix.getData(),
          deltaPseuoRange);
      double biasTemp = bias;
      biasTemp += deltaPosition[3];

      double tempdet = new LUDecomposition(temp).getDeterminant();
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--det(temp)=" + tempdet);

      // Apply corrections to the position estimate
      double[] positionTemp = new double[4];
      for (int i = 0; i < 4; ++i) {
        positionTemp[i] = positionSolution[i] + deltaPosition[i];
      }

      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--deltaPosition=" + deltaPosition[0]
          + "," + deltaPosition[1] + "," + deltaPosition[2] + "," + deltaPosition[3]);

      double[] estiPseuoRangeTemp = new double[pseudoliteNum];
      double[] deltaPseuoRangeTemp = new double[pseudoliteNum];
      for (int i = 0; i < pseudoliteNum; ++i) {
        double[] r = {indoorAntennasXyz[i][0] - positionTemp[0],
            indoorAntennasXyz[i][1] - positionTemp[1],
            indoorAntennasXyz[i][2] - positionTemp[2]};
        estiPseuoRangeTemp[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + biasTemp;
        deltaPseuoRangeTemp[i] = pseudoliteToUserRange[i] - estiPseuoRangeTemp[i];
      }
      double delta2 = 0.0;
      for (double deltaPr : deltaPseuoRangeTemp) {
        delta2 += Math.pow(deltaPr, 2);
      }

      double norm = 0.0;
      for (double dp : deltaPosition) {
        norm += Math.pow(dp, 2);
      }
      norm = Math.sqrt(norm);

      Log.d("伪卫星最小二乘", "delta1:" + delta1);
      Log.d("伪卫星最小二乘", "delta2:" + delta2);
      //Log.d("伪卫星最小二乘", "norm:" + norm);

      if (delta1 >= delta2) {    //accept
        lambda /= miu;
        bias = biasTemp;
        for (int i = 0; i < 4; ++i) {
          positionSolution[i] = positionTemp[i];
        }
        delta1 = delta2;
        error = norm;
        Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--error=" + error);
      } else {
        lambda *= miu;
      }
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--lambda:" + lambda);
      --calTimes;
      System.out.print(calTimes+":\t");
      System.out.println(error);
    }
    /*while (error >= LEAST_SQUARE_TOLERANCE_METERS && calTimes > 0) {
      RealMatrix connectionMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          indoorAntennasXyz, positionSolution));

      RealMatrix GTG = connectionMatrix.transpose().multiply(connectionMatrix);

      double[][] t = GTG.getData();
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--GTG="+t[0][0]+","
          +t[0][1]+","+t[0][2]+","+t[0][3]+"\n"+t[1][0]+","+t[1][1]+","+t[1][2]+","+t[1][3]+"\n"
          +t[2][0]+","+t[2][1]+","+t[2][2]+","+t[2][3]+"\n"+t[3][0]+","+t[3][1]+","+t[3][2]+","+t[3][3]);
      double tempdet = new LUDecomposition(GTG).getDeterminant();
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--det(GTG)="+tempdet);

      RealMatrix hMatrix = new LUDecomposition(GTG).getSolver().getInverse();
      RealMatrix buffConnectionMatrix = hMatrix.multiply(connectionMatrix.transpose());
      deltaPosition = GpsMathOperations.matrixByColVectMultiplication(buffConnectionMatrix.getData(),
          deltaPseuoRange);

      bias += deltaPosition[3];

      // Apply corrections to the position estimate
      positionSolution[0] += deltaPosition[0];
      positionSolution[1] += deltaPosition[1];
      positionSolution[2] += deltaPosition[2];
      positionSolution[3] += deltaPosition[3];

      for (int i = 0; i < pseudoliteNum; ++i) {
        double[] r = {indoorAntennasXyz[i][0] - positionSolution[0],
            indoorAntennasXyz[i][1] - positionSolution[1],
            indoorAntennasXyz[i][2] - positionSolution[2]};
        estiPseuoRange[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + bias;
        deltaPseuoRange[i] = pseudoliteToUserRange[i] - estiPseuoRange[i];
      }

      error = 0.0;
      for (double dp : deltaPosition) {
        error += Math.abs(dp);
      }
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--error="+error);
      --calTimes;
    }*/
    /*RealMatrix covarianceMatrixM2 =
        new Array2DRowRealMatrix(satPosPseudorangeResAndCovMatrix.covarianceMatrixMetersSquare);
    LUDecomposition ludCovMatrixM2 = new LUDecomposition(covarianceMatrixM2);
    double det = ludCovMatrixM2.getDeterminant();

    while (error >= LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS && calTimes > 0) {
      RealMatrix geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          indoorAntennasXyz, positionSolution));
      RealMatrix weightedGeometryMatrix;
      if (det <= DOUBLE_ROUND_OFF_TOLERANCE) {
        RealMatrix temp = geometryMatrix.transpose().multiply(geometryMatrix);
        RealMatrix hMatrix = new LUDecomposition(temp).getSolver().getInverse();
        weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose());
      } else {
        RealMatrix weightMatrixMetersMinus2 = ludCovMatrixM2.getSolver().getInverse();
        RealMatrix hMatrix =
            calculateHMatrix(weightMatrixMetersMinus2, geometryMatrix);
        weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose())
            .multiply(weightMatrixMetersMinus2);
      }
      deltaPosition = GpsMathOperations.matrixByColVectMultiplication(weightedGeometryMatrix.getData(),
          deltaPseuoRange);

      // Apply corrections to the position estimate
      positionSolution[0] += deltaPosition[0];
      positionSolution[1] += deltaPosition[1];
      positionSolution[2] += deltaPosition[2];
      positionSolution[3] += deltaPosition[3];
      bias += deltaPosition[3];
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--bias="+bias);
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--deltaPosition="+deltaPosition[0]
          +","+deltaPosition[1]+","+deltaPosition[2]+","+deltaPosition[3]);
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--Position="+positionSolution[0]
          +","+positionSolution[1]+","+positionSolution[2]+","+positionSolution[3]);

      for (int i = 0; i < pseudoliteNum; ++i) {
        double[] r = {indoorAntennasXyz[i][0] - positionSolution[0],
            indoorAntennasXyz[i][1] - positionSolution[1],
            indoorAntennasXyz[i][2] - positionSolution[2]};
        estiPseuoRange[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + bias;
        deltaPseuoRange[i] = pseudoliteToUserRange[i] - estiPseuoRange[i];
        Log.d("伪卫星最小二乘", "estiPseudoRange["+i+"]:"+estiPseuoRange[i]);
        Log.d("伪卫星最小二乘", "deltaPseuoRange["+i+"]:"+deltaPseuoRange[i]);
      }

      error = 0.0;
      for (int i = 0; i < 3; ++i) {
        error += Math.pow(deltaPosition[i], 2);
      }
      error = Math.sqrt(error);
      Log.d("伪卫星最小二乘", "第"+(100-calTimes)+"次迭代--error="+error);
    }*/
    if (calTimes <= 0) {
      Log.d("伪卫星最小二乘", "迭代并未收敛");
      positionSolution[0] = Double.NaN;
      positionSolution[1] = Double.NaN;
      positionSolution[2] = Double.NaN;
      positionSolution[3] = Double.NaN;
    }
    calculateGeoidMeters = false;
  }

  /**
   * Least square solution to calculate the pseu-position given the navigation message, pseudorange
   * and accumulated delta range measurements.
   *
   * <p>The method fills the pseu-position in ECEF coordinates and receiver clock
   * offset in meters and clock offset rate in meters per second
   *
   * <p>One can choose between no smoothing, using the carrier phase measurements (accumulated delta
   * range) or the doppler measurements (pseudorange rate) for smoothing the pseudorange. The
   * smoothing is applied only if time has changed below a specific threshold since last invocation.
   *
   * <p>Source for least squares:
   *
   * <ul>
   *   <li>http://www.u-blox.com/images/downloads/Product_Docs/GPS_Compendium%28GPS-X-02007%29.pdf
   *       page 81 - 85
   *   <li>Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and applications’
   *       page 412 - 414
   * </ul>
   *
   * <p>Sources for smoothing pseudorange with carrier phase measurements:
   *
   * <ul>
   *   <li>Satellite Communications and Navigation Systems book, page 424,
   *   <li>Principles of GNSS, Inertial, and Multisensor Integrated Navigation Systems, page 388,
   *       389.
   * </ul>
   *
   * <p>The function does not modify the smoothed measurement list {@code
   * immutableSmoothedSatellitesToReceiverMeasurements}
   *
   * @param navMessageProto parameters of the navigation message
   * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to {@link
   *     GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for computing the
   *     position solution.
   * @param receiverGPSTowAtReceptionSeconds Receiver estimate of GPS time of week (seconds)
   * @param receiverGPSWeek Receiver estimate of GPS week (0-1024+)
   * @param dayOfYear1To366 The day of the year between 1 and 366
   * @param positionSolutionECEF Solution array of the following format:
   *        [0-2] xyz pseu-position of user.
   *        [3] clock bias of user.
   */
  public SatellitesPositionPseudorangesResidualAndCovarianceMatrix calculateUserPositionLeastSquare(
      GpsNavMessageProto navMessageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGPSWeek,
      int dayOfYear1To366,
      double[] positionSolutionECEF)
      throws Exception {

    // Use PseudorangeSmoother to smooth the pseudorange according to: Satellite Communications and
    // Navigation Systems book, page 424 and Principles of GNSS, Inertial, and Multisensor
    // Integrated Navigation Systems, page 388, 389.
    double[] deltaPositionMeters;
    List<GpsMeasurementWithRangeAndUncertainty> immutableSmoothedSatellitesToReceiverMeasurements =
        pseudorangeSmoother.updatePseudorangeSmoothingResult(
            Collections.unmodifiableList(usefulSatellitesToReceiverMeasurements));
    List<GpsMeasurementWithRangeAndUncertainty> mutableSmoothedSatellitesToReceiverMeasurements =
        Lists.newArrayList(immutableSmoothedSatellitesToReceiverMeasurements);
    int numberOfUsefulSatellites =
        getNumberOfUsefulSatellites(mutableSmoothedSatellitesToReceiverMeasurements);
    // Least square position solution is supported only if 4 or more satellites visible
    Preconditions.checkArgument(numberOfUsefulSatellites >= MINIMUM_NUMER_OF_SATELLITES,
        "At least 4 satellites have to be visible... Only 3D mode is supported...");
    boolean repeatLeastSquare = false;
    SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight;

    // Calculate satellites' positions, measurement residuals per visible satellite and
    // weight matrix for the iterative least square
    boolean doAtmosphericCorrections = false;
    satPosPseudorangeResidualAndWeight =
        calculateSatPosAndPseudorangeResidual(
            navMessageProto,
            mutableSmoothedSatellitesToReceiverMeasurements,
            receiverGPSTowAtReceptionSeconds,
            receiverGPSWeek,
            dayOfYear1To366,
            positionSolutionECEF,
            doAtmosphericCorrections);

    // Calculate the geometry matrix according to "Global Positioning System: Theory and
    // Applications", Parkinson and Spilker page 413
    RealMatrix covarianceMatrixM2 =
        new Array2DRowRealMatrix(satPosPseudorangeResidualAndWeight.covarianceMatrixMetersSquare);
    geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
        satPosPseudorangeResidualAndWeight.satellitesPositionsMeters,
        positionSolutionECEF));
    RealMatrix weightedGeometryMatrix;
    RealMatrix weightMatrixMetersMinus2 = null;
    // Apply weighted least square only if the covariance matrix is not singular (has a non-zero
    // determinant), otherwise apply ordinary least square. The reason is to ignore reported
    // signal to noise ratios by the receiver that can lead to such singularities
    LUDecomposition ludCovMatrixM2 = new LUDecomposition(covarianceMatrixM2);
    double det = ludCovMatrixM2.getDeterminant();

    if (det <= DOUBLE_ROUND_OFF_TOLERANCE) {
      // Do not weight the geometry matrix if covariance matrix is singular.
      // weightedGeometryMatrix = geometryMatrix;
      // 上面的不太对吧，测试换成413页的equation9看看
      RealMatrix temp = geometryMatrix.transpose().multiply(geometryMatrix);
      RealMatrix hMatrix = new LUDecomposition(temp).getSolver().getInverse();
      weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose());
    } else {
      weightMatrixMetersMinus2 = ludCovMatrixM2.getSolver().getInverse();
      RealMatrix hMatrix =
          calculateHMatrix(weightMatrixMetersMinus2, geometryMatrix);
      weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose())
          .multiply(weightMatrixMetersMinus2);
    }

    // Equation 9 page 413 from "Global Positioning System: Theory and Applicaitons", Parkinson
    // and Spilker
    deltaPositionMeters =
        GpsMathOperations.matrixByColVectMultiplication(weightedGeometryMatrix.getData(),
            satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

    // Apply corrections to the position estimate
    positionSolutionECEF[0] += deltaPositionMeters[0];
    positionSolutionECEF[1] += deltaPositionMeters[1];
    positionSolutionECEF[2] += deltaPositionMeters[2];
    positionSolutionECEF[3] += deltaPositionMeters[3];
    // Iterate applying corrections to the position solution until correction is below threshold
    satPosPseudorangeResidualAndWeight =
        applyWeightedLeastSquare(
            navMessageProto,
            mutableSmoothedSatellitesToReceiverMeasurements,
            receiverGPSTowAtReceptionSeconds,
            receiverGPSWeek,
            dayOfYear1To366,
            positionSolutionECEF,
            deltaPositionMeters,
            doAtmosphericCorrections,
            satPosPseudorangeResidualAndWeight,
            weightMatrixMetersMinus2);

    //calculateGeoidMeters = false;


    GeodeticLlaValues latLngAlt =
        Ecef2LlaConverter.convertECEFToLLACloseForm(
            positionSolutionECEF[0],
            positionSolutionECEF[1],
            positionSolutionECEF[2]);
    double lat = Math.toDegrees(latLngAlt.latitudeRadians);
    double lng = Math.toDegrees(latLngAlt.longitudeRadians);
    double alt = latLngAlt.altitudeMeters;
    Log.d("伪位置",
        "Latitude, Longitude, Altitude: "
            + lat
            + " "
            + lng
            + " "
            + alt);

    return satPosPseudorangeResidualAndWeight;
  }

  /**
   * Calculates the measurement connection matrix H as a function of weightMatrix and
   * geometryMatrix.
   *
   * <p> H = (geometryMatrixTransposed * Weight * geometryMatrix) ^ -1
   *
   * <p> Reference: Global Positioning System: Signals, Measurements, and Performance, P207
   * @param weightMatrix Weights for computing H Matrix
   * @return H Matrix
   */
  private RealMatrix calculateHMatrix
      (RealMatrix weightMatrix, RealMatrix geometryMatrix){

    RealMatrix tempH = geometryMatrix.transpose().multiply(weightMatrix).multiply(geometryMatrix);
    return new LUDecomposition(tempH).getSolver().getInverse();
  }

  /**
   * Applies weighted least square iterations and corrects to the position solution until correction
   * is below threshold. An exception is thrown if the maximum number of iterations:
   * {@value #MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS} is reached without convergence.
   */
  private SatellitesPositionPseudorangesResidualAndCovarianceMatrix applyWeightedLeastSquare(
      GpsNavMessageProto navMessageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGPSWeek,
      int dayOfYear1To366,
      double[] positionSolutionECEF,
      double[] deltaPositionMeters,
      boolean doAtmosphericCorrections,
      SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight,
      RealMatrix weightMatrixMetersMinus2)
      throws Exception {
    RealMatrix weightedGeometryMatrix;
    int numberOfIterations = 0;

    while ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
        + Math.abs(deltaPositionMeters[2])) >= LEAST_SQUARE_TOLERANCE_METERS) {
      // Apply ionospheric and tropospheric corrections only if the applied correction to
      // position is below a specific threshold
      if ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
          + Math.abs(deltaPositionMeters[2])) < ATMPOSPHERIC_CORRECTIONS_THRESHOLD_METERS) {
        // 暂时不做电离层和对流层校正
        // doAtmosphericCorrections = true;
      }
      // Calculate satellites' positions, measurement residual per visible satellite and
      // weight matrix for the iterative least square
      satPosPseudorangeResidualAndWeight =
          calculateSatPosAndPseudorangeResidual(
              navMessageProto,
              usefulSatellitesToReceiverMeasurements,
              receiverGPSTowAtReceptionSeconds,
              receiverGPSWeek,
              dayOfYear1To366,
              positionSolutionECEF,
              doAtmosphericCorrections);

      // Calculate the geometry matrix according to "Global Positioning System: Theory and
      // Applications", Parkinson and Spilker page 413
      geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          satPosPseudorangeResidualAndWeight.satellitesPositionsMeters, positionSolutionECEF));
      // Apply weighted least square only if the covariance matrix is
      // not singular (has a non-zero determinant), otherwise apply ordinary least square.
      // The reason is to ignore reported signal to noise ratios by the receiver that can
      // lead to such singularities
      if (weightMatrixMetersMinus2 == null) {
        // weightedGeometryMatrix = geometryMatrix;
        // 这里也应该和上面一样改一下吧
        RealMatrix temp = geometryMatrix.transpose().multiply(geometryMatrix);
        RealMatrix hMatrix = new LUDecomposition(temp).getSolver().getInverse();
        weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose());
      } else {
        RealMatrix hMatrix =
            calculateHMatrix(weightMatrixMetersMinus2, geometryMatrix);
        weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose())
            .multiply(weightMatrixMetersMinus2);
      }

      // Equation 9 page 413 from "Global Positioning System: Theory and Applicaitons",
      // Parkinson and Spilker
      deltaPositionMeters =
          GpsMathOperations.matrixByColVectMultiplication(
              weightedGeometryMatrix.getData(),
              satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

      // Apply corrections to the position estimate
      positionSolutionECEF[0] += deltaPositionMeters[0];
      positionSolutionECEF[1] += deltaPositionMeters[1];
      positionSolutionECEF[2] += deltaPositionMeters[2];
      positionSolutionECEF[3] += deltaPositionMeters[3];
      numberOfIterations++;
      Preconditions.checkArgument(numberOfIterations <= MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS,
          "Maximum number of least square iterations reached without convergance...");
    }
    return satPosPseudorangeResidualAndWeight;
  }


  /**
   * Calculates position of all visible satellites and pseudorange measurement residual
   * (difference of measured to predicted pseudoranges) needed for the least square computation. The
   * result is stored in an instance of {@link
   * SatellitesPositionPseudorangesResidualAndCovarianceMatrix}
   *
   * @param navMeassageProto parameters of the navigation message
   * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to {@link
   *     GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for computing the
   *     position solution
   * @param receiverGPSTowAtReceptionSeconds Receiver estimate of GPS time of week (seconds)
   * @param receiverGpsWeek Receiver estimate of GPS week (0-1024+)
   * @param dayOfYear1To366 The day of the year between 1 and 366
   * @param userPositionECEFMeters receiver ECEF position in meters
   * @param doAtmosphericCorrections boolean indicating if atmospheric range corrections should be
   *     applied
   * @return SatellitesPositionPseudorangesResidualAndCovarianceMatrix Object containing satellite
   *     prns, satellite positions in ECEF, pseudorange residuals and covariance matrix.
   */
  public SatellitesPositionPseudorangesResidualAndCovarianceMatrix
      calculateSatPosAndPseudorangeResidual(
          GpsNavMessageProto navMeassageProto,
          List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
          double receiverGPSTowAtReceptionSeconds,
          int receiverGpsWeek,
          int dayOfYear1To366,
          double[] userPositionECEFMeters,
          boolean doAtmosphericCorrections)
          throws Exception {
    int numberOfUsefulSatellites =
        getNumberOfUsefulSatellites(usefulSatellitesToReceiverMeasurements);
    // deltaPseudorange is the pseudorange measurement residual
    double[] deltaPseudorangesMeters = new double[numberOfUsefulSatellites];
    double[][] satellitesPositionsECEFMeters = new double[numberOfUsefulSatellites][3];

    // satellite PRNs
    int[] satellitePRNs = new int[numberOfUsefulSatellites];

    // Ionospheric model parameters
    double[] alpha =
            {navMeassageProto.iono.alpha[0], navMeassageProto.iono.alpha[1],
                    navMeassageProto.iono.alpha[2], navMeassageProto.iono.alpha[3]};
    double[] beta = {navMeassageProto.iono.beta[0], navMeassageProto.iono.beta[1],
            navMeassageProto.iono.beta[2], navMeassageProto.iono.beta[3]};
    // Weight matrix for the weighted least square
    RealMatrix covarianceMatrixMetersSquare =
        new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);
    calculateSatPosAndResiduals(
        navMeassageProto,
        usefulSatellitesToReceiverMeasurements,
        receiverGPSTowAtReceptionSeconds,
        receiverGpsWeek,
        dayOfYear1To366,
        userPositionECEFMeters,
        doAtmosphericCorrections,
        deltaPseudorangesMeters,
        satellitesPositionsECEFMeters,
        satellitePRNs,
        alpha,
        beta,
        covarianceMatrixMetersSquare);

    return new SatellitesPositionPseudorangesResidualAndCovarianceMatrix(satellitePRNs,
        satellitesPositionsECEFMeters, deltaPseudorangesMeters,
        covarianceMatrixMetersSquare.getData(), doAtmosphericCorrections);
  }

  /**
   * Calculates and fill the position of all visible satellites:
   * {@code satellitesPositionsECEFMeters}, pseudorange measurement residual (difference of
   * measured to predicted pseudoranges): {@code deltaPseudorangesMeters} and covariance matrix from
   * the weighted least square: {@code covarianceMatrixMetersSquare}. An array of the satellite PRNs
   * {@code satellitePRNs} is as well filled.
   */
  private void calculateSatPosAndResiduals(
      GpsNavMessageProto navMessageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGpsWeek,
      int dayOfYear1To366,
      double[] userPositionECEFMeters,
      boolean doAtmosphericCorrections,
      double[] deltaPseudorangesMeters,
      double[][] satellitesPositionsECEFMeters,
      int[] satellitePRNs,
      double[] alpha,
      double[] beta,
      RealMatrix covarianceMatrixMetersSquare)
      throws Exception {
    // user position without the clock estimate
    double[] userPositionTempECEFMeters =
        {userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]};
    int satsCounter = 0;

    // 移到这里来
    // Correct the receiver time of week with the estimated receiver clock bias
    receiverGPSTowAtReceptionSeconds -= userPositionECEFMeters[3] / SPEED_OF_LIGHT_MPS;

    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
        GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);
        // 应该修正一次就够了，移到循环外去
        /*// Correct the receiver time of week with the estimated receiver clock bias
        receiverGPSTowAtReceptionSeconds =
            receiverGPSTowAtReceptionSeconds - userPositionECEFMeters[3] / SPEED_OF_LIGHT_MPS;*/

        double pseudorangeMeasurementMeters =
            usefulSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
        double pseudorangeUncertaintyMeters =
            usefulSatellitesToReceiverMeasurements.get(i).pseudorangeUncertaintyMeters;

        // Assuming uncorrelated pseudorange measurements, the covariance matrix will be diagonal as
        // follows
        covarianceMatrixMetersSquare.setEntry(satsCounter, satsCounter,
            pseudorangeUncertaintyMeters * pseudorangeUncertaintyMeters);

        // Calculate time of week at transmission time corrected with the satellite clock drift
        GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
            calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                receiverGpsWeek, pseudorangeMeasurementMeters);

        // calculate satellite position and velocity
        PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
            .calculateSatellitePositionAndVelocityFromEphemeris(ephemeridesProto,
                correctedTowAndWeek.gpsTimeOfWeekSeconds, correctedTowAndWeek.weekNumber,
                userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]);

        satellitesPositionsECEFMeters[satsCounter][0] = satPosECEFMetersVelocityMPS.positionXMeters;
        satellitesPositionsECEFMeters[satsCounter][1] = satPosECEFMetersVelocityMPS.positionYMeters;
        satellitesPositionsECEFMeters[satsCounter][2] = satPosECEFMetersVelocityMPS.positionZMeters;

        // Calculate ionospheric and tropospheric corrections
        double ionosphericCorrectionMeters;
        double troposphericCorrectionMeters;
        if (doAtmosphericCorrections) {
          ionosphericCorrectionMeters =
              IonosphericModel.ionoKloboucharCorrectionSeconds(
                      userPositionTempECEFMeters,
                      satellitesPositionsECEFMeters[satsCounter],
                      correctedTowAndWeek.gpsTimeOfWeekSeconds,
                      alpha,
                      beta,
                      IonosphericModel.L1_FREQ_HZ)
                  * SPEED_OF_LIGHT_MPS;

          troposphericCorrectionMeters =
              calculateTroposphericCorrectionMeters(
                  dayOfYear1To366,
                  satellitesPositionsECEFMeters,
                  userPositionTempECEFMeters,
                  satsCounter);
        } else {
          troposphericCorrectionMeters = 0.0;
          ionosphericCorrectionMeters = 0.0;
        }
        double predictedPseudorangeMeters =
            calculatePredictedPseudorange(userPositionECEFMeters, satellitesPositionsECEFMeters,
                userPositionTempECEFMeters, satsCounter, ephemeridesProto, correctedTowAndWeek,
                ionosphericCorrectionMeters, troposphericCorrectionMeters);

        // Pseudorange residual (difference of measured to predicted pseudoranges)
        deltaPseudorangesMeters[satsCounter] =
            pseudorangeMeasurementMeters - predictedPseudorangeMeters;

        Log.d("假装没有伪卫星时的定位过程", satsCounter+":pseudorangeMeasurementMeters="
            +pseudorangeMeasurementMeters+",predictedPseudorangeMeters="+predictedPseudorangeMeters
            +",deltaPseudorangesMeters["+satsCounter+"]="+deltaPseudorangesMeters[satsCounter]);

        // Satellite PRNs
        satellitePRNs[satsCounter] = i + 1;
        satsCounter++;
      }
    }
  }

  /** Searches ephemerides list for the ephemeris associated with current satellite in process */
  private GpsEphemerisProto getEphemerisForSatellite(GpsNavMessageProto navMeassageProto,
                                                     int satPrn) {
    List<GpsEphemerisProto> ephemeridesList
            = new ArrayList<GpsEphemerisProto>(Arrays.asList(navMeassageProto.ephemerids));
    GpsEphemerisProto ephemeridesProto = null;
    int ephemerisPrn = 0;
    for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
      ephemerisPrn = ephProtoFromList.prn;
      if (ephemerisPrn == satPrn) {
        ephemeridesProto = ephProtoFromList;
        break;
      }
    }
    return ephemeridesProto;
  }

  /** Calculates predicted pseudorange in meters */
  private double calculatePredictedPseudorange(
      double[] userPositionECEFMeters,
      double[][] satellitesPositionsECEFMeters,
      double[] userPositionNoClockECEFMeters,
      int satsCounter,
      GpsEphemerisProto ephemeridesProto,
      GpsTimeOfWeekAndWeekNumber correctedTowAndWeek,
      double ionosphericCorrectionMeters,
      double troposphericCorrectionMeters)
      throws Exception {
    // Calcualte the satellite clock drift
    double satelliteClockCorrectionMeters =
        SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
                ephemeridesProto,
                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                correctedTowAndWeek.weekNumber)
            .satelliteClockCorrectionMeters;

    double satelliteToUserDistanceMeters =
        GpsMathOperations.vectorNorm(GpsMathOperations.subtractTwoVectors(
            satellitesPositionsECEFMeters[satsCounter], userPositionNoClockECEFMeters));
    // Predicted pseudorange
    double predictedPseudorangeMeters =
        satelliteToUserDistanceMeters - satelliteClockCorrectionMeters + ionosphericCorrectionMeters
            + troposphericCorrectionMeters + userPositionECEFMeters[3];
    return predictedPseudorangeMeters;
  }

  /** Calculates the Gps tropospheric correction in meters */
  private double calculateTroposphericCorrectionMeters(int dayOfYear1To366,
      double[][] satellitesPositionsECEFMeters, double[] userPositionTempECEFMeters,
      int satsCounter) {
    double troposphericCorrectionMeters;
    TopocentricAEDValues elevationAzimuthDist =
        EcefToTopocentricConverter.convertCartesianToTopocentericRadMeters(
            userPositionTempECEFMeters, GpsMathOperations.subtractTwoVectors(
                satellitesPositionsECEFMeters[satsCounter], userPositionTempECEFMeters));

    GeodeticLlaValues lla =
        Ecef2LlaConverter.convertECEFToLLACloseForm(userPositionTempECEFMeters[0],
            userPositionTempECEFMeters[1], userPositionTempECEFMeters[2]);

    // Geoid of the area where the receiver is located is calculated once and used for the
    // rest of the dataset as it change very slowly over wide area. This to save the delay
    // associated with accessing Google Elevation API. We assume this very first iteration of WLS
    // will compute the correct altitude above the ellipsoid of the ground at the latitude and
    // longitude
    if (calculateGeoidMeters) {
      double elevationAboveSeaLevelMeters = 0;
      if (elevationApiHelper == null){
        System.out.println("No Google API key is set. Elevation above sea level is set to "
            + "default 0 meters. This may cause inaccuracy in tropospheric correction.");
      } else {
        try {
          elevationAboveSeaLevelMeters = elevationApiHelper
              .getElevationAboveSeaLevelMeters(
                  Math.toDegrees(lla.latitudeRadians), Math.toDegrees(lla.longitudeRadians)
              );
        } catch (Exception e){
          e.printStackTrace();
          System.out.println("Error when getting elevation from Google Server. "
              + "Could be wrong Api key or network error. Elevation above sea level is set to "
              + "default 0 meters. This may cause inaccuracy in tropospheric correction.");
        }
      }

      geoidHeightMeters = ElevationApiHelper.calculateGeoidHeightMeters(
              lla.altitudeMeters,
              elevationAboveSeaLevelMeters
      );
      troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
          elevationAzimuthDist.elevationRadians, lla.latitudeRadians, elevationAboveSeaLevelMeters,
          dayOfYear1To366);
    } else {
      troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
          elevationAzimuthDist.elevationRadians, lla.latitudeRadians,
          lla.altitudeMeters - geoidHeightMeters, dayOfYear1To366);
    }
    return troposphericCorrectionMeters;
  }

  /**
   * Gets the number of useful satellites from a list of
   * {@link GpsMeasurementWithRangeAndUncertainty}.
   */
  private int getNumberOfUsefulSatellites(
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements) {
    // calculate the number of useful satellites
    int numberOfUsefulSatellites = 0;
    for (int i = 0; i < usefulSatellitesToReceiverMeasurements.size(); i++) {
      if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
        numberOfUsefulSatellites++;
      }
    }
    return numberOfUsefulSatellites;
  }

  /**
   * Computes the GPS time of week at the time of transmission and as well the corrected GPS week
   * taking into consideration week rollover. The returned GPS time of week is corrected by the
   * computed satellite clock drift. The result is stored in an instance of
   * {@link GpsTimeOfWeekAndWeekNumber}
   *
   * @param ephemerisProto parameters of the navigation message
   * @param receiverGpsTowAtReceptionSeconds Receiver estimate of GPS time of week when signal was
   *        received (seconds)
   * @param receiverGpsWeek Receiver estimate of GPS week (0-1024+)
   * @param pseudorangeMeters Measured pseudorange in meters
   * @return GpsTimeOfWeekAndWeekNumber Object containing Gps time of week and week number.
   */
  private static GpsTimeOfWeekAndWeekNumber calculateCorrectedTransmitTowAndWeek(
      GpsEphemerisProto ephemerisProto, double receiverGpsTowAtReceptionSeconds,
      int receiverGpsWeek, double pseudorangeMeters) throws Exception {
    // GPS time of week at time of transmission: Gps time corrected for transit time (page 98 ICD
    // GPS 200)
    double receiverGpsTowAtTimeOfTransmission =
        receiverGpsTowAtReceptionSeconds - pseudorangeMeters / SPEED_OF_LIGHT_MPS;

    // Adjust for week rollover
    if (receiverGpsTowAtTimeOfTransmission < 0) {
      receiverGpsTowAtTimeOfTransmission += SECONDS_IN_WEEK;
      receiverGpsWeek -= 1;
    } else if (receiverGpsTowAtTimeOfTransmission > SECONDS_IN_WEEK) {
      receiverGpsTowAtTimeOfTransmission -= SECONDS_IN_WEEK;
      receiverGpsWeek += 1;
    }

    // Compute the satellite clock correction term (Seconds)
    double clockCorrectionSeconds =
        SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
            ephemerisProto, receiverGpsTowAtTimeOfTransmission,
            receiverGpsWeek).satelliteClockCorrectionMeters / SPEED_OF_LIGHT_MPS;

    // Correct with the satellite clock correction term
    double receiverGpsTowAtTimeOfTransmissionCorrectedSec =
        receiverGpsTowAtTimeOfTransmission + clockCorrectionSeconds;

    // Adjust for week rollover due to satellite clock correction
    if (receiverGpsTowAtTimeOfTransmissionCorrectedSec < 0.0) {
      receiverGpsTowAtTimeOfTransmissionCorrectedSec += SECONDS_IN_WEEK;
      receiverGpsWeek -= 1;
    }
    if (receiverGpsTowAtTimeOfTransmissionCorrectedSec > SECONDS_IN_WEEK) {
      receiverGpsTowAtTimeOfTransmissionCorrectedSec -= SECONDS_IN_WEEK;
      receiverGpsWeek += 1;
    }
    return new GpsTimeOfWeekAndWeekNumber(receiverGpsTowAtTimeOfTransmissionCorrectedSec,
        receiverGpsWeek);
  }

  /**
   * Calculates the Geometry matrix (describing user to satellite geometry) given a list of
   * satellite positions in ECEF coordinates in meters and the user position in ECEF in meters.
   *
   * <p>The geometry matrix has four columns, and rows equal to the number of satellites. For each
   * of the rows (i.e. for each of the satellites used), the columns are filled with the normalized
   * line–of-sight vectors and 1 s for the fourth column.
   *
   * <p>Source: Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and
   * applications’ page 413
   */
  private static double[][] calculateGeometryMatrix(double[][] satellitePositionsECEFMeters,
      double[] userPositionECEFMeters) {

    double[][] geometeryMatrix = new double[satellitePositionsECEFMeters.length][4];
    for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
      geometeryMatrix[i][3] = 1;
    }
    // iterate over all satellites
    for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
      double[] r = {satellitePositionsECEFMeters[i][0] - userPositionECEFMeters[0],
          satellitePositionsECEFMeters[i][1] - userPositionECEFMeters[1],
          satellitePositionsECEFMeters[i][2] - userPositionECEFMeters[2]};
      double norm = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2));
      for (int j = 0; j < 3; j++) {
        geometeryMatrix[i][j] =
            (userPositionECEFMeters[j] - satellitePositionsECEFMeters[i][j]) / norm;
      }
    }
    return geometeryMatrix;
  }

  /**
   * Class containing satellites' PRNs, satellites' positions in ECEF meters, the pseudorange
   * residual per visible satellite in meters and the covariance matrix of the
   * pseudoranges in meters square
   */
  protected static class SatellitesPositionPseudorangesResidualAndCovarianceMatrix {

    /** Satellites' PRNs */
    protected final int[] satellitePRNs;

    /** ECEF positions (meters) of useful satellites */
    protected final double[][] satellitesPositionsMeters;

    /** Pseudorange measurement residuals (difference of measured to predicted pseudoranges) */
    protected final double[] pseudorangeResidualsMeters;

    /** Pseudorange covariance Matrix for the weighted least squares (meters square) */
    protected final double[][] covarianceMatrixMetersSquare;

    /**  */
    protected final boolean doAtmosphericCorrections;

    /** Constructor */
    private SatellitesPositionPseudorangesResidualAndCovarianceMatrix(int[] satellitePRNs,
        double[][] satellitesPositionsMeters, double[] pseudorangeResidualsMeters,
        double[][] covarianceMatrixMetersSquare, boolean doAtmosphericCorrections) {
      this.satellitePRNs = satellitePRNs;
      this.satellitesPositionsMeters = satellitesPositionsMeters;
      this.pseudorangeResidualsMeters = pseudorangeResidualsMeters;
      this.covarianceMatrixMetersSquare = covarianceMatrixMetersSquare;
      this.doAtmosphericCorrections = doAtmosphericCorrections;
    }

  }

  /**
   * Class containing GPS time of week in seconds and GPS week number
   */
  private static class GpsTimeOfWeekAndWeekNumber {
    /** GPS time of week in seconds */
    private final double gpsTimeOfWeekSeconds;

    /** GPS week number */
    private final int weekNumber;

    /** Constructor */
    private GpsTimeOfWeekAndWeekNumber(double gpsTimeOfWeekSeconds, int weekNumber) {
      this.gpsTimeOfWeekSeconds = gpsTimeOfWeekSeconds;
      this.weekNumber = weekNumber;
    }
  }
  
  /**
   * Uses the common reception time approach to calculate pseudoranges from the time of week
   * measurements reported by the receiver according to http://cdn.intechopen.com/pdfs-wm/27712.pdf.
   * As well computes the pseudoranges uncertainties for each input satellite
   */
  @VisibleForTesting
  static List<GpsMeasurementWithRangeAndUncertainty> computePseudorangeAndUncertainties(
      List<GpsMeasurement> usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      double biasNanos) {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        Arrays.asList(
            new GpsMeasurementWithRangeAndUncertainty
                [GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES]);
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (usefulSatellitesToTOWNs[i] != null) {
        double tRxSeconds = (usefulSatellitesToReceiverMeasurements.get(i).arrivalTimeSinceGpsWeekNs
            - usefulSatellitesToReceiverMeasurements.get(i).timeOffsetNanos - biasNanos) * SECONDS_PER_NANO;
        double tTxSeconds = usefulSatellitesToTOWNs[i] * SECONDS_PER_NANO;
        double prS = tRxSeconds - tTxSeconds;
        if (prS > SECONDS_IN_WEEK/2)
          prS -= Math.round(prS/SECONDS_IN_WEEK) * SECONDS_IN_WEEK;
        double pseudorangeMeters = prS * SPEED_OF_LIGHT_MPS;

        double signalToNoiseRatioLinear =
            Math.pow(10, usefulSatellitesToReceiverMeasurements.get(i).signalToNoiseRatioDb / 10.0);
        // From Global Positoning System book, Misra and Enge, page 416, the uncertainty of the
        // pseudorange measurement is calculated next.
        // For GPS C/A code chip width Tc = 1 microseconds. Narrow correlator with spacing d = 0.1
        // chip and an average time of DLL correlator T of 20 milliseconds are used.
        double sigmaMeters =
            SPEED_OF_LIGHT_MPS
                * GPS_CHIP_WIDTH_T_C_SEC
                * Math.sqrt(
                    GPS_CORRELATOR_SPACING_IN_CHIPS
                        / (4 * GPS_DLL_AVERAGING_TIME_SEC * signalToNoiseRatioLinear));
        usefulSatellitesToPseudorangeMeasurements.set(
            i,
            new GpsMeasurementWithRangeAndUncertainty(
                usefulSatellitesToReceiverMeasurements.get(i), pseudorangeMeters, sigmaMeters));
      }
    }
    return usefulSatellitesToPseudorangeMeasurements;
  }

  public double[] getAntennaToSatPseudorangesMeters() {
    return this.mAntennaToSatPseudorangesMeters;
  }

  public double[] getAntennaToUserPseudorangesMeters() {
    return this.mAntennaToUserPseudorangesMeters;
  }

}
