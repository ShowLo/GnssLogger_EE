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

  private final double[] outdoorAntennaLla = {40.0, 118.0, 100.0};
  private final double[][] indoorAntennasXyz = {{4.307, 2.591, 2.696},
      {-10.229, -1.914, 2.598}, {1.509, -6.977, 2.781}, {-4.867, 5.233, 2.598}};
  private final double[] outdoorToIndoorRange = {12.0, 16.0, 12.0, 16.0};

  public double[] getOutdoorAntennaLla() {
    return outdoorAntennaLla;
  }

  public double[][] getIndoorAntennasXyz() {
    return indoorAntennasXyz;
  }

  public double[] getOutdoorToIndoorRange() {
    return outdoorToIndoorRange;
  }
}