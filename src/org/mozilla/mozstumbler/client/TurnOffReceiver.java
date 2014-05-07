package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.mozilla.mozstumbler.client.MainActivity;

/** Test low power in adb with am broadcast -a android.intent.action.BATTERY_LOW
 * Test cancel button in notification list by swiping down on the entry for the
 * stumbler, and [X] Stop Scanning will appear.
 */
 public final class TurnOffReceiver extends BroadcastReceiver {
    private static final String LOGTAG = TurnOffReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOGTAG, "onReceive!");

        context.sendBroadcast(new Intent(MainActivity.ACTION_STOP_SCANNING));
    }
}
