/*
 * edited by Chen Jiarong, Department of Electronic Engineering, Tsinghua University
 */

package com.google.android.apps.location.gps.pseudolite;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

public class BaiduMapFragment extends Fragment {

  private BaiduMap map;
  private MapView mapView;
  private CoordinateConverter converter;
  //Marker图标
  private BitmapDescriptor bitmap;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    //在使用SDK各组件之前初始化context信息，传入ApplicationContext
    SDKInitializer.initialize(getActivity().getApplicationContext());
    View rootView = inflater.inflate(R.layout.fragment_map, container, false);
    mapView = rootView.findViewById(R.id.map);
    map = mapView.getMap();
    converter = new CoordinateConverter();
    converter.from(CoordinateConverter.CoordType.GPS);
    bitmap = BitmapDescriptorFactory.fromResource(R.drawable.ic_marker);

    return rootView;
  }

  public void updateMarker(double latitude, double longitude) {
    //清除之前的marker
    map.clear();
    // 将GPS设备采集的原始GPS坐标转换成百度坐标
    LatLng sourceLatLng = new LatLng(latitude, longitude);
    converter.coord(sourceLatLng);
    LatLng point = converter.convert();

    //构建MarkerOption，用于在地图上添加Marker
    OverlayOptions option = new MarkerOptions()
        .position(point)
        .icon(bitmap);
    //在地图上添加Marker，并显示
    map.addOverlay(option);

    //设置中心点
    MapStatus mMapStatus = new MapStatus.Builder()
        .target(point)
        .zoom(18)
        .build();
    //定义MapStatusUpdate对象，以便描述地图状态将要发生的变化
    MapStatusUpdate mMapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mMapStatus);
    //改变地图状态
    map.setMapStatus(mMapStatusUpdate);
  }

  @Override
  public void onPause() {
    mapView.onPause();
    super.onPause();
  }

  @Override
  public void onResume() {
    mapView.onResume();
    super.onResume();
  }

  @Override
  public void onDestroy() {
    mapView.onDestroy();
    super.onDestroy();
  }
}