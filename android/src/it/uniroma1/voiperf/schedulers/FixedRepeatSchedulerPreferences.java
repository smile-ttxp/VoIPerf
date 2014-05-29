package it.uniroma1.voiperf.schedulers;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.R;
import it.uniroma1.voiperf.VoIPerfPreferenceActivity;
import it.uniroma1.voiperf.logging.Logger;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class FixedRepeatSchedulerPreferences extends PreferenceActivity {

    private static final String sTag = FixedRepeatSchedulerPreferences.class.getName();
    
    private final OnPreferenceChangeListener mPrefChangeListener =
                                    new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String prefKey = preference.getKey();
            if (prefKey.equals(Config.FIXED_REPEAT_SCHEDULER_PROFILE_PREF_KEY)) {
                try {
                    Integer val = Integer.parseInt((String) newValue);
                    if (val <= 0) {
                        Toast.makeText(FixedRepeatSchedulerPreferences.this,
                                       getString(R.string.fixedRepeatSchedulerInvalidInterval),
                                       Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return true;
                } catch (ClassCastException e) {
                    Logger.e(sTag, "Cannot cast checkin interval preference value to Integer");
                    return false;
                } catch (NumberFormatException e) {
                    Logger.e(sTag, "Cannot cast checkin interval preference value to Integer");
                    return false;
                }
            } else if (prefKey.equals(Config.FIXED_REPEAT_SCHEDULER_BATTERY_MIN_PREF_KEY)) {
                try {
                    Integer val = Integer.parseInt((String) newValue);
                    if (val < 0 || val > 100) {
                        Toast.makeText(FixedRepeatSchedulerPreferences.this,
                                       getString(R.string.fixedRepeatSchedulerInvalidBattery),
                                       Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return true;
                } catch (ClassCastException e) {
                    Logger.e(sTag, "Cannot cast battery preference value to Integer");
                    return false;
                  } catch (NumberFormatException e) {
                    Logger.e(sTag, "Cannot cast battery preference value to Integer");
                    return false;
                }
            }
            return true;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d(sTag, "onCreate");
        super.onCreate(savedInstanceState);     
        addPreferencesFromResource(R.xml.preferences);       
        Preference intervalPref = findPreference(Config.FIXED_REPEAT_SCHEDULER_PROFILE_PREF_KEY);
        intervalPref.setOnPreferenceChangeListener(mPrefChangeListener);
        
        Preference batteryPref = findPreference(Config.FIXED_REPEAT_SCHEDULER_BATTERY_MIN_PREF_KEY);
        batteryPref.setOnPreferenceChangeListener(mPrefChangeListener);
    }
    
    /**
     * When the user leaves the preferences pane,
     * the new preferences must be loaded by the other components
     */
    @Override
    protected void onPause() {
        Logger.d(sTag, "onPause");
        sendBroadcast(new Intent(VoIPerfPreferenceActivity.PREF_CHANGED_ACTION));
        super.onDestroy();
    }
}
