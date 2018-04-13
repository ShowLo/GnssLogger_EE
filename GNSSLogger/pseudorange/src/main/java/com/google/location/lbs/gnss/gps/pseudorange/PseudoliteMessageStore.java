/*
 * edited by Chen Jiarong, Department of Electronic Engineering, Tsinghua University
 */

package com.google.location.lbs.gnss.gps.pseudorange;

/**
 * A class to store the information of pseudolites, including the position of outdoor
 * receiving antenna and indoor forwarding antennas
 * 储存伪卫星信息，包括室外接收天线和室内转发天线的位置
 */
public class PseudoliteMessageStore {

  private final double[] outdoorAntennaLla = {40.001636, 116.329972, 100.0};
  /*private final double[][] indoorAntennasXyz = {{6.020, 4.141, 1.719},
      {-5.869, 3.331, 2.510}, {6.679, -5.623, 2.582}, {-5.833, -4.825, 3.143}};*/
  private final double[][] indoorAntennasXyz = {{1, 1, 1},
      {-1, 1, 1}, {1, -1, 1}, {-1, -1, 1}};

  public double[] getOutdoorAntennaLla() {
    return outdoorAntennaLla;
  }

  public double[][] getIndoorAntennasXyz() {
    return indoorAntennasXyz;
  }
}