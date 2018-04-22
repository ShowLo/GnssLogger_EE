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

import android.util.Log;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Random;

class TestLeastSquare {
  private static final double LEAST_SQUARE_TOLERANCE_METERS = 4.0e-8;
  private static final double LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS = 0.01;
  private static final int MAX_NUM_OF_ITERATION = 100;


  public void calculatePseudolitePositioningLeastSquare(
      PseudoliteMessageStore pseudoliteMessageStore,
      double[] positionSolution)
      throws Exception {

    double[][] indoorAntennasXyz = pseudoliteMessageStore.getIndoorAntennasXyz();
    int pseudoliteNum = indoorAntennasXyz.length;

    double[] pseudoliteToUserRange = new double[pseudoliteNum];

    // 用最小二乘进行定位
    double bias = 0.0;
    double[] estiPseuoRange = new double[pseudoliteNum];
    double[] deltaPseuoRange = new double[pseudoliteNum];
    double[] deltaPosition;
    int calTimes = MAX_NUM_OF_ITERATION;
    double error = 10;

    for (int i = 0; i < pseudoliteNum; ++i) {
      double[] r = {indoorAntennasXyz[i][0] - positionSolution[0],
          indoorAntennasXyz[i][1] - positionSolution[1],
          indoorAntennasXyz[i][2] - positionSolution[2]};
      estiPseuoRange[i] = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2)) + bias;
    }

    Random random = new Random();
    double[] simulationPos = {10.0, 10.0, 0.0};
    for (int satsCounter = 0; satsCounter < pseudoliteNum; ++satsCounter) {

      // 仿真用，假设用户位置在simulationPos，同时叠加高斯噪声
      double[] r = {indoorAntennasXyz[satsCounter][0] - simulationPos[0],
          indoorAntennasXyz[satsCounter][1] - simulationPos[1],
          indoorAntennasXyz[satsCounter][2] - simulationPos[2]};
      pseudoliteToUserRange[satsCounter] = Math.sqrt(Math.pow(r[0], 2) +
          Math.pow(r[1], 2) + Math.pow(r[2], 2)) + random.nextGaussian();

      deltaPseuoRange[satsCounter] = pseudoliteToUserRange[satsCounter] - estiPseuoRange[satsCounter];
      Log.d("测试伪卫星最小二乘", "处理后的伪卫星到用户伪距" + satsCounter + ":" + pseudoliteToUserRange[satsCounter]
          + " estiPseudoRange[" + satsCounter + "]:" + estiPseuoRange[satsCounter] +
          " deltaPseuoRange[" + satsCounter + "]:" + deltaPseuoRange[satsCounter]);
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

    /*lambda = 0.1;
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
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--bias=" + bias);

      double[][] t = temp.getData();
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--temp=" + t[0][0] + ","
          + t[0][1] + "," + t[0][2] + "," + t[0][3] + "\n" + t[1][0] + "," + t[1][1] + "," + t[1][2] + "," + t[1][3] + "\n"
          + t[2][0] + "," + t[2][1] + "," + t[2][2] + "," + t[2][3] + "\n" + t[3][0] + "," + t[3][1] + "," + t[3][2] + "," + t[3][3]);

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

      if (delta1 > delta2) {    //accept
        //lambda = lambda / (1 + norm);
        lambda /= miu;
        bias = biasTemp;
        for (int i = 0; i < 4; ++i) {
          positionSolution[i] = positionTemp[i];
        }
        delta1 = delta2;
        error = norm;
        Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--error=" + error);
      } else {
        //lambda = lambda + (delta2 - delta1) / (1 + norm);
        lambda *= miu;
      }
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--lambda:" + lambda);
      --calTimes;
    }*/
    while (error >= LEAST_SQUARE_PSEUDOLITE_POSITIONING_METERS && calTimes > 0) {
      RealMatrix connectionMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          indoorAntennasXyz, positionSolution));

      RealMatrix GTG = connectionMatrix.transpose().multiply(connectionMatrix);

      double[][] t = GTG.getData();
      Log.d("测试伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--GTG=" + t[0][0] + ","
          + t[0][1] + "," + t[0][2] + "," + t[0][3] + "\n" + t[1][0] + "," + t[1][1] + "," + t[1][2] + "," + t[1][3] + "\n"
          + t[2][0] + "," + t[2][1] + "," + t[2][2] + "," + t[2][3] + "\n" + t[3][0] + "," + t[3][1] + "," + t[3][2] + "," + t[3][3]);
      double tempdet = new LUDecomposition(GTG).getDeterminant();
      Log.d("测试伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--det(GTG)=" + tempdet);

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
        error += Math.pow(dp, 2);
      }
      error = Math.sqrt(error);
      Log.d("伪卫星最小二乘", "第" + (MAX_NUM_OF_ITERATION - calTimes) + "次迭代--error=" + error);
      --calTimes;
    }
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
  }

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
}
