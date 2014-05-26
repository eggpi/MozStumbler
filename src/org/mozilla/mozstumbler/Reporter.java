package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.mozilla.mozstumbler.cellscanner.CellInfo;
import org.mozilla.mozstumbler.cellscanner.CellScanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mozilla.mozstumbler.provider.DatabaseContract.*;

final class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();

    /**
     * The maximum time of observation
     */
    private static final int REPORTER_WINDOW  = 24 * 60 * 60 * 1000; //ms

    /**
     * The maximum number of Wi-Fi access points in a single observation
     */
    private static final int WIFI_COUNT_WATERMARK = 100;

    /**
     * The maximum number of cells in a single observation
     */
    private static final int CELLS_COUNT_WATERMARK = 50;

    private final Context       mContext;
    private final ContentResolver mContentResolver;
    private final int             mPhoneType;

    private StumblerBundle     mBundle;

    Reporter(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        resetData();
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this,
                new IntentFilter(ScannerService.MESSAGE_TOPIC));

        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneType = tm.getPhoneType();
    }

    private void resetData() {
        mBundle = null;
    }

    void flush() {
        reportCollectedLocation();
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");
        flush();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (!ScannerService.MESSAGE_TOPIC.equals(action)) {
            Log.e(LOGTAG, "Received an unknown intent");
            return;
        }

        long time = intent.getLongExtra("time", System.currentTimeMillis());
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

        Location currentPosition = mBundle != null ? mBundle.getGpsPosition() : null;

        if (currentPosition != null && Math.abs(time - currentPosition.getTime()) > REPORTER_WINDOW) {
            reportCollectedLocation();
        }

        if (WifiScanner.WIFI_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.WIFI_SCANNER_ARG_SCAN_RESULTS);
            putWifiResults(results);
        } else if (CellScanner.CELL_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            List<CellInfo> results = intent.getParcelableArrayListExtra(CellScanner.CELL_SCANNER_ARG_CELLS);
            putCellResults(results);
        } else if (GPSScanner.GPS_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            reportCollectedLocation();
            Location newPosition = intent.getParcelableExtra(GPSScanner.GPS_SCANNER_ARG_LOCATION);
            mBundle = newPosition != null ? new StumblerBundle(newPosition, mPhoneType) : mBundle;
        } else {
            Log.d(LOGTAG, "Intent ignored with Subject: " + subject);
            return; // Intent not aimed at the Reporter (it is possibly for UI instead)
        }

        if (mBundle != null &&
            (mBundle.getWifiData().size() > WIFI_COUNT_WATERMARK ||
             mBundle.getCellData().size() > CELLS_COUNT_WATERMARK)) {
            reportCollectedLocation();
        }
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mBundle == null) {
            return;
        }

        Map<String, ScanResult> currentWifiData = mBundle.getWifiData();
        for (ScanResult result : results) {
            String key = result.BSSID;
            if (!currentWifiData.containsKey(key)) {
                currentWifiData.put(key, result);
            }
        }
    }

    private void putCellResults(List<CellInfo> cells) {
        if (mBundle == null) {
            return;
        }

        Map<String, CellInfo> currentCellData = mBundle.getCellData();
        for (CellInfo result : cells) {
            String key = result.getCellIdentity();
            if (!currentCellData.containsKey(key)) {
                currentCellData.put(key, result);
            }
        }
    }

    private void reportCollectedLocation() {
        if (mBundle == null) {
            return;
        }

        /*
        Intent broadcast = new Intent(StumblerService.MESSAGE_TOPIC);
        broadcast.putExtra("stumblerBundle", mBundle);
        mContext.sendBroadcast(broadcast);
        */

        try {
            JSONObject mlsObject = mBundle.toMLSJSON();
            Log.d(LOGTAG, mlsObject.toString());
            mBundle.getGpsPosition().setTime(System.currentTimeMillis());
        } catch (JSONException e) {
            Log.w(LOGTAG, "JSON exception: ", e);
            // FIXME clear mBundle?
        }
    }

    private void reportCollectedLocation(Location gpsPosition, Collection<ScanResult> wifiInfo, String radioType,
                                 Collection<CellInfo> cellInfo) {
        if (gpsPosition == null) {
            return;
        }

        ContentValues values = new ContentValues(10);
        values.put(Reports.TIME, gpsPosition.getTime());
        values.put(Reports.LAT, Math.floor(gpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        values.put(Reports.LON, Math.floor(gpsPosition.getLongitude() * 1.0E6) / 1.0E6);
        if (gpsPosition.hasAltitude()) {
            values.put(Reports.ALTITUDE, Math.round(gpsPosition.getAltitude()));
        }
        if (gpsPosition.hasAccuracy()) {
            values.put(Reports.ACCURACY, (int) Math.ceil(gpsPosition.getAccuracy()));
        }

        values.put(Reports.RADIO, radioType);
        JSONArray cellJSON = new JSONArray();
        for (CellInfo cell : cellInfo) {
            cellJSON.put(cell.toJSONObject());
        }
        values.put(Reports.CELL, cellJSON.toString());
        values.put(Reports.CELL_COUNT, cellJSON.length());

        JSONArray wifiJSON = new JSONArray();
        try {
        for (ScanResult wifi : wifiInfo) {
            JSONObject jsonItem = new JSONObject();
            jsonItem.put("key", wifi.BSSID);
            jsonItem.put("frequency", wifi.frequency);
            jsonItem.put("signal", wifi.level);
            wifiJSON.put(jsonItem);
        }
        }catch (JSONException jsonex) {
            Log.w(LOGTAG, "JSON exception", jsonex);
        }
        values.put(Reports.WIFI, wifiJSON.toString());
        values.put(Reports.WIFI_COUNT, wifiJSON.length());

        mContentResolver.insert(Reports.CONTENT_URI, values);
    }
}
