package org.mozilla.mozstumbler.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.service.cellscanner.CellScanner;

class Scanner {
  private static final String LOGTAG = Scanner.class.getName();

  private final Context  mContext;
  private boolean        mIsScanning;

  private GPSScanner     mGPSScanner;
  private WifiScanner    mWifiScanner;
  private CellScanner    mCellScanner;

  Scanner(Context context) {
    mContext = context;
    mGPSScanner  = new GPSScanner(context);
    mWifiScanner = new WifiScanner(context);
    mCellScanner = new CellScanner(context);
  }

  void startScanning() {
    if (mIsScanning) {
      return;
    }
    Log.d(LOGTAG, "Scanning started...");

    mGPSScanner.start();
    mWifiScanner.start();
    mCellScanner.start();

    mIsScanning = true;

    // FIXME investigate why this needs to update the UI, didn't the UI trigger this
    Intent startIntent = new Intent(MainActivity.ACTION_UPDATE_UI);
    mContext.sendBroadcast(startIntent);
  }

  void stopScanning() {
    if (!mIsScanning) {
      return;
    }

    Log.d(LOGTAG, "Scanning stopped");

    mGPSScanner.stop();
    mWifiScanner.stop();
    mCellScanner.stop();

    mIsScanning = false;

  // FIXME investigate why this needs to update the UI, didn't the UI trigger this
  Intent startIntent = new Intent(MainActivity.ACTION_UPDATE_UI);
  mContext.sendBroadcast(startIntent);
  }

  boolean isScanning() {
    return mIsScanning;
  }

  int getAPCount() {
     return mWifiScanner.getAPCount();
  }

  int getVisibleAPCount() {
     return mWifiScanner.getVisibleAPCount();
  }

  int getWifiStatus() {
      return mWifiScanner.getStatus();
  }

  int getCellInfoCount() {
     return mCellScanner.getCellInfoCount();
  }

  int getCurrentCellInfoCount() {
     return mCellScanner.getCurrentCellInfoCount();
  }

  int getLocationCount() {
     return mGPSScanner.getLocationCount();
  }

  double getLatitude() {
     return mGPSScanner.getLatitude();
  }

  double getLongitude() {
     return mGPSScanner.getLongitude();
  }

  void checkPrefs() {
      mGPSScanner.checkPrefs();
  }

  boolean isGeofenced() {
      return mGPSScanner.isGeofenced();
  }
}