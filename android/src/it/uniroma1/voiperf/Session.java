package it.uniroma1.voiperf;

import it.uniroma1.voiperf.results.ResultsDatabase;
import it.uniroma1.voiperf.schedulers.Scheduler;
import it.uniroma1.voiperf.util.NetworkStatus;
import it.uniroma1.voiperf.util.PhoneStatus;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Session extends Application {

    private static Scheduler sScheduler = null;
    private static boolean sIsForegroundServiceRunning = false;
    private static boolean sIsBoundToService = false;
    private static Context sApplicationContext = null;
    private static NetworkStatus sNetworkStatus = null;
    private static PhoneStatus sPhoneStatus = null;
    private static long sNextMeasurementTimestamp = 0;
    private static long sLastMeasurementTimestamp = 0;
    
    private static long sLastUpdateTimestamp = 0;
    public static int VOIPERF_UPDATE_CHECK_PERIOD = 5 * 60;
    
    public static int MAX_RESULTS_VIEW = 100;
    public static MeasurementService sMeasurementService = null;
    public static ResultsDatabase sResultsDatabaseHandler = null;
    public static VoIPerfUpdateManager sVoIPerfUpdateManager = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
    }
    
    public static Context getGlobalContext() {
        return sApplicationContext;
    }
    
    public static void setGlobalContext(Context context) {
        sApplicationContext = context;
    }
    
    public static void setForegroundServiceRunning(boolean val) {
        sIsForegroundServiceRunning = val;
    }
    
    public static ResultsDatabase getDatabaseHandler() {
    	if (sResultsDatabaseHandler == null) {
    		sResultsDatabaseHandler = new ResultsDatabase(getGlobalContext());
    	}
    	return sResultsDatabaseHandler;
    }
    
    public static VoIPerfUpdateManager getUpdateManager() {
    	if (sVoIPerfUpdateManager == null) {
    		sVoIPerfUpdateManager = new VoIPerfUpdateManager();
    	}
    	return sVoIPerfUpdateManager;
    }
    
    public static boolean isForegroundServiceRunning() {
        return sIsForegroundServiceRunning;
    }
    
    public static void saveForegroundServiceRunning(boolean value) {
        SharedPreferences prefs = PreferenceManager
                                        .getDefaultSharedPreferences(sApplicationContext);
        prefs.edit().putBoolean(Config.FOREGROUND_SERVICE_RUNNING_KEY, value).commit();
    }
    
    public static boolean loadSavedForegroundServiceRunning() {
        SharedPreferences prefs = PreferenceManager
                                        .getDefaultSharedPreferences(sApplicationContext);
        return prefs.getBoolean(Config.FOREGROUND_SERVICE_RUNNING_KEY,
                Config.FOREGROUND_SERVICE_RUNNING_DEFAULT);
    }
    
    public static NetworkStatus getNetworkStatus() {
        return sNetworkStatus;
    }
    
    public static void setNetworkStatus(NetworkStatus status) {
        sNetworkStatus = status;
    }
    
    public static PhoneStatus getPhoneStatus() {
        return sPhoneStatus;
    }
    
    public static void setPhoneStatus(PhoneStatus status) {
        sPhoneStatus = status;
    }
    
    public static boolean isSchedulerRunning() {
        return sScheduler != null && sScheduler.isRunning();
    }
    
    public static void setScheduler(Scheduler scheduler) {
        sScheduler = scheduler;
    }
    
    public static Scheduler getScheduler() {
        return sScheduler;
    }
    
    public static void setBoundToService(boolean val) {
        sIsBoundToService = val;
    }
    
    public static boolean isBoundToService() {
        return sIsBoundToService;
    }
   
    public static String getStatus() {
        if (isBoundToService()) {
            return "Running";
        } else {
            return "Stopped";
        }
    }
    
    private static String timestampToString(long timestamp) {
        if (timestamp == 0) {
            return "";
        }
        Date d = new Date(timestamp * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM, HH:mm");
        return sdf.format(d); 
    }
    
    public static String getLastMeasurementDate() {
       return timestampToString(sLastMeasurementTimestamp);
    }
    
    public synchronized static void setNextMeasurement(long timestamp) {
        sNextMeasurementTimestamp = timestamp;
    }
    
    public static String getNextMeasurementDate() {
        return timestampToString(sNextMeasurementTimestamp);
    }
    
    public static synchronized boolean checkVoIPerfUpdate() {
        long now = System.currentTimeMillis() / 1000;
        if (now - sLastUpdateTimestamp > VOIPERF_UPDATE_CHECK_PERIOD) {
            sLastUpdateTimestamp = now;
            return true;
        }
        
        return false;
    }
}
