/*
 * edited by Chen Jiarong, Department of Electronic Engineering, Tsinghua University
 */

package com.google.location.lbs.gnss.gps.pseudorange;

import java.util.HashMap;

/**
 * A class to store the information of pseudolites, including the position of outdoor
 * receiving antenna and indoor forwarding antennas
 * 储存伪卫星信息，包括室外接收天线和室内转发天线的位置
 */
public class PseudoliteMessageStore {
  // 室外接收天线位置
  private double[] outdoorAntennaLla;// = {40.0, 118.0, 100.0};
  // 卫星id
  private int[] satelliteId;// = {3, 23, 19, 10};
  // 室内伪卫星位置
  private double[][] indoorAntennasXyz;// = {{1.509, -6.977, 2.781},
      //{4.307, 2.591, 2.696}, {-4.867, 5.233, 2.598}, {-10.229, -1.914, 2.598}};
  // 各天线长度
  private double[] outdoorToIndoorRange;// = {12.0, 12.0, 16.0, 16.0};
  // 各通道延时
  private double[] channelDelay;// = {10.0, 20.0, 30.0, 0.0};

  public void setOutdoorAntennaLla(double[] outdoorAntennaLla) {
    this.outdoorAntennaLla = outdoorAntennaLla;
  }

  public void setSatelliteId(int[] satelliteId) {
    this.satelliteId = satelliteId;
  }

  public void setIndoorAntennasXyz(double[][] indoorAntennasXyz) {
    this.indoorAntennasXyz = indoorAntennasXyz;
  }

  public void setOutdoorToIndoorRange(double[] outdoorToIndoorRange) {
    this.outdoorToIndoorRange = outdoorToIndoorRange;
  }

  public void setChannelDelay(double[] channelDelay) {
    this.channelDelay = channelDelay;
  }

  public double[] getOutdoorAntennaLla() {
    return outdoorAntennaLla;
  }

  public int[] getSatelliteId() {
    return satelliteId;
  }

  public double[][] getIndoorAntennasXyz() {
    return indoorAntennasXyz;
  }

  public double[] getOutdoorToIndoorRange() {
    return outdoorToIndoorRange;
  }

  public double[] getChannelDelay() {
    return channelDelay;
  }
}