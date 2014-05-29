package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.measurements.VoIPerfMeasurement;
import it.uniroma1.voiperf.schedulers.FixedRepeatScheduler;
import it.uniroma1.voiperf.schedulers.SchedulerProfile;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

/**
 * Activity that handles user preferences.
 */
public class VoIPerfPreferenceActivity extends PreferenceActivity {

    public static final String PREF_CHANGED_ACTION = FixedRepeatScheduler
                                    .class.getPackage().getName() + ".PREF_CHANGED_ACTION";
    
    private static final String sTag = VoIPerfPreferenceActivity.class.getName();
    
    private final OnPreferenceChangeListener mPrefChangeListener = 
                                    new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String prefKey = preference.getKey();
            if (prefKey.equals(Config.FIXED_REPEAT_SCHEDULER_PROFILE_PREF_KEY)) {
                try {
                    Integer val = Integer.parseInt((String) newValue);
                    if (val < 0) {
                        Toast.makeText(VoIPerfPreferenceActivity.this,
                                       getString(R.string.fixedRepeatSchedulerInvalidInterval),
                                       Toast.LENGTH_LONG).show();
                        return false;
                    }
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
                        Toast.makeText(VoIPerfPreferenceActivity.this,
                                       getString(R.string.fixedRepeatSchedulerInvalidBattery),
                                       Toast.LENGTH_LONG).show();
                        return false;
                    }
                } catch (ClassCastException e) {
                    Logger.e(sTag, "Cannot cast battery preference value to Integer");
                    return false;
                  } catch (NumberFormatException e) {
                    Logger.e(sTag, "Cannot cast battery preference value to Integer");
                    return false;
                }
            }
            // Tell all the interested components that the preferences have changed
            sendBroadcast(new Intent(PREF_CHANGED_ACTION));
            return true;
        }
        
    };
    
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d(sTag, "onCreate");
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.preferences);
        
        ListPreference profilePref = (ListPreference) findPreference(
                Config.FIXED_REPEAT_SCHEDULER_PROFILE_PREF_KEY);
        profilePref.setOnPreferenceChangeListener(mPrefChangeListener);
        
        SchedulerProfile[] profiles = SchedulerProfile.getProfiles();
        CharSequence[] entries = new CharSequence[profiles.length];
        CharSequence[] values = new CharSequence[profiles.length];
        for (int i = 0; i < profiles.length; ++i) {
            double dailyCostMB = (profiles[i].getNumberOfDailyMeasurements() *
                                 VoIPerfMeasurement.MEASUREMENT_COST_KB) / 1024.0;
            int weeklyCostMB = (int) dailyCostMB * 7;
            
            entries[i] = "< " + weeklyCostMB + "MB " + getString(R.string.costPerWeek);
            values[i] = String.valueOf(i);
        }
        profilePref.setEntries(entries);
        profilePref.setEntryValues(values);
        
        Preference batteryPref = findPreference(Config.FIXED_REPEAT_SCHEDULER_BATTERY_MIN_PREF_KEY);
        batteryPref.setOnPreferenceChangeListener(mPrefChangeListener);
    }
    
    /**
     * When the user leaves the preferences pane,
     * the new preferences must be loaded by the other components
     */
    @Override
    protected void onDestroy() {
        Logger.d(sTag, "onDestroy");
        // Not really necessary, but just to be sure that the new options are loaded
        sendBroadcast(new Intent(PREF_CHANGED_ACTION));
        super.onDestroy();
    }
}
