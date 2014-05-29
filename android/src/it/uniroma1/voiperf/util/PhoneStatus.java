package it.uniroma1.voiperf.util;

import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;

public class PhoneStatus {

    private static final String sTag = PhoneStatus.class.getName();
    
    private boolean mUserIsPresent;
    private long mLastUserPresent;
    
    private boolean mScreenIsOn;
    private long mLastScreenOn;
    private long mLastScreenOff;
    
    private boolean mPowerIsConnected;
    private long mLastPowerConnected;
    private long mLastPowerDisconnected;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                Logger.d(sTag, "Saving user present");
                mUserIsPresent = true;
                mLastUserPresent = System.currentTimeMillis();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                Logger.d(sTag, "Saving screen on");
                mScreenIsOn = true;
                mLastScreenOn = System.currentTimeMillis();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Logger.d(sTag, "Saving screen off");
                mScreenIsOn = false;
                mUserIsPresent = false;
                mLastScreenOff = System.currentTimeMillis();
            } else if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                Logger.d(sTag, "Saving power connected");
                mPowerIsConnected = true;
                mLastPowerConnected = System.currentTimeMillis();
            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                Logger.d(sTag, "Saving power disconnected");
                mPowerIsConnected = false;
                mLastPowerDisconnected = System.currentTimeMillis();
            }
        }
    };
    
    public void start() {
        Logger.d(sTag, "start");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        Session.getGlobalContext().registerReceiver(mReceiver, filter);
    }
    
    public void stop() {
        Logger.d(sTag, "stop");
        Session.getGlobalContext().unregisterReceiver(mReceiver);
    }
    
    public double currentBatteryPercentage() {
        Context context = Session.getGlobalContext();
        Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        return level * 100.0 / scale;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getDeviceInfo() {
        JSONObject info = new JSONObject();

        Context context = Session.getGlobalContext();
        TelephonyManager tManager = (TelephonyManager) context
                                        .getSystemService(Context.TELEPHONY_SERVICE);
        info.put("device_id", tManager.getDeviceId());
        info.put("unique_id", tManager.getDeviceId()); // TODO: does it always give a unique id?
        info.put("network_operator_name", tManager.getNetworkOperatorName());
        info.put("network_country_iso", tManager.getNetworkCountryIso());
        info.put("sim_country_iso", tManager.getSimCountryIso());
        info.put("manufacturer", Build.MANUFACTURER);
        info.put("device", Build.DEVICE);
        info.put("model", Build.MODEL);
        info.put("os_version", Build.VERSION.CODENAME + "," + 
                               Build.VERSION.INCREMENTAL + "," + 
                               Build.VERSION.RELEASE + "," +
                               Build.VERSION.SDK_INT);
        
        try {
            PackageInfo pinfo = context.getPackageManager()
                                        .getPackageInfo(context.getPackageName(), 0);
            info.put("app_version", pinfo.versionCode);
            info.put("app_version_name", pinfo.versionName);
        } catch (Exception e) {
            Logger.w(sTag, "Could not find this app version!");
            info.put("app_version", -1);
            info.put("app_version_name", null);
        }
        
        JSONArray accounts = new JSONArray();
        for (Account account: AccountManager.get(context).getAccounts()) {
            JSONObject a = new JSONObject();
            a.put("name", account.name);
            a.put("type", account.type);
            accounts.add(a);
        }
        info.put("accounts", accounts);
        
        return info;
    }
    
    @SuppressWarnings("unchecked")
    public JSONObject getPhoneStatus() {
        JSONObject state = new JSONObject();

        state.put("user_present", mUserIsPresent);
        state.put("last_user_present", mLastUserPresent);
        
        state.put("screen_is_on", mScreenIsOn);
        state.put("last_screen_on", mLastScreenOn);
        state.put("last_screen_off", mLastScreenOff);

        state.put("power_is_connected", mPowerIsConnected);
        state.put("last_power_connected", mLastPowerConnected);
        state.put("last_power_disconnected", mLastPowerDisconnected);
        state.put("battery_percentage", currentBatteryPercentage());
        
        return state;
    }
}
