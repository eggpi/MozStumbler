/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.utils.LocationAdapter;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.concurrent.atomic.AtomicInteger;

/*
This class provides MLS locations by calling HTTP methods against the MLS.
 */
public class MLSLocationGetter extends AsyncTask<String, Void, Location> {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MLSLocationGetter.class);
    private static AtomicInteger sRequestCounter = new AtomicInteger(0);
    private final int MAX_REQUESTS = 10;
    private ILocationService mls = (ILocationService)
                                    ServiceLocator.getInstance()
                                            .getService(ILocationService.class);

    private MLSLocationGetterCallback mCallback;
    private byte[] mQueryMLSBytes;
    private boolean mIsBadRequest;

    public MLSLocationGetter(MLSLocationGetterCallback callback, JSONObject mlsQueryObj) {
        mCallback = callback;
        mQueryMLSBytes = mlsQueryObj.toString().getBytes();
    }

    @Override
    public Location doInBackground(String... params) {

        int requests = sRequestCounter.incrementAndGet();
        if (requests > MAX_REQUESTS) {
            return null;
        }

        IResponse resp = mls.search(mQueryMLSBytes, null, false);

        if (resp == null) {
            Log.i(LOG_TAG, "Error processing search request");
            return null;
        }

        if (resp.isErrorCode400BadRequest()) {
            //TODO detect malformed request, and clear out mlsrequest on observation point
            mIsBadRequest = true;
            return null;
        }

        JSONObject response;
        try {
            response = new JSONObject(resp.body());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error deserializing JSON. " + e.toString(), e);
            return null;
        }

        return LocationAdapter.fromJSON(response);
    }

    @Override
    protected void onPostExecute(Location location) {
        sRequestCounter.decrementAndGet();
        if (location == null) {
            mCallback.errorMLSResponse(mIsBadRequest);
        } else {
            mCallback.setMLSResponseLocation(location);
        }
    }

    public interface MLSLocationGetterCallback {
        void setMLSResponseLocation(Location loc);

        void errorMLSResponse(boolean stopRequesting);
    }
}
