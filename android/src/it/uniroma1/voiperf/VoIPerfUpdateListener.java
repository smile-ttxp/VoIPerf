package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class VoIPerfUpdateListener extends BroadcastReceiver {

    private static final String sTag = VoIPerfUpdateListener.class.getName();
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d(sTag, "onReceive");
        Logger.i(sTag, "Application updated.");
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(Config.START_ON_BOOT_PREF_KEY, Config.START_ON_BOOT_DEFAULT)) {
            Logger.i(sTag, "Starting the scheduler");
            Intent serviceIntent = new Intent(context, MeasurementService.class);
            serviceIntent.putExtra(MeasurementService.EXTRA_START_SCHEDULER, true);
            context.startService(serviceIntent);
        }
    }
}
