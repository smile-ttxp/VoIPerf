package it.uniroma1.voiperf.util;

import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;

import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

public class NetworkStatus {

    private static final String sTag = NetworkStatus.class.getName();
    
    private int mCurrentMobileRSSI;
    private long mLastDataActivity;
    
    private boolean mWiFiIsConnected;
    private long mLastWiFiConnected;
    private long mLastWiFiDisconnected;
    
    private long mLastWiFiScanResults;
    
    private final PhoneStateListener mStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            if (signalStrength.isGsm()) {
                mCurrentMobileRSSI = Utils.translateGSMRssi(signalStrength.getGsmSignalStrength());
            } else {
                mCurrentMobileRSSI = signalStrength.getCdmaDbm();
            }
        }
        @Override
        public void onDataActivity(int direction) {
            switch (direction) {
                case TelephonyManager.DATA_ACTIVITY_IN:
                case TelephonyManager.DATA_ACTIVITY_OUT:
                case TelephonyManager.DATA_ACTIVITY_INOUT:
                    mLastDataActivity = System.currentTimeMillis();
                    break;
                case TelephonyManager.DATA_ACTIVITY_NONE:
                    break;
            }
        }
    };
    
    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                    mWiFiIsConnected = true;
                    mLastWiFiConnected = System.currentTimeMillis();
                } else {
                    mWiFiIsConnected = false;
                    mLastWiFiDisconnected = System.currentTimeMillis();
                }
            } else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                mLastWiFiScanResults = System.currentTimeMillis();
            }
        }
    };
    
    public void start() {
        Logger.d(sTag, "start");
        TelephonyManager telephonyManager = (TelephonyManager) Session.getGlobalContext()
                                                       .getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                                                PhoneStateListener.LISTEN_DATA_ACTIVITY);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(WifiManager.EXTRA_SUPPLICANT_CONNECTED);
        Session.getGlobalContext().registerReceiver(mWifiStateReceiver, filter);
    }
    
    public void stop() {
        Logger.d(sTag, "stop");
        TelephonyManager telephonyManager = (TelephonyManager) Session.getGlobalContext()
                                                       .getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mStateListener, PhoneStateListener.LISTEN_NONE);
        
        Session.getGlobalContext().unregisterReceiver(mWifiStateReceiver);
    }
    
    public static boolean isConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) Session.getGlobalContext()
                                        .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ninfo = connectivityManager.getActiveNetworkInfo();
        return ninfo != null && ninfo.isConnected();
    }
    
    @SuppressWarnings("unchecked")
    public JSONObject getNetworkStatus() {
        JSONObject state = new JSONObject();
        
        Context context = Session.getGlobalContext();
        ConnectivityManager connectivityManager = 
                       (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        TelephonyManager telephonyManager =
                       (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        JSONObject activec = new JSONObject();
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            activec.put("is_available", networkInfo.isAvailable());
            activec.put("is_connected", networkInfo.isConnected());
            activec.put("connection_type", networkInfo.getTypeName());
            activec.put("connection_subtype", networkInfo.getSubtypeName());
        } else {
            activec.put("is_available", null);
            activec.put("is_connected", null);
            activec.put("connection_type", null);
            activec.put("connection_subtype", null);
        }
        state.put("active_connection", activec);
        
        JSONObject wifi = new JSONObject();
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            wifi.put("ssid", wifiInfo.getSSID());
            wifi.put("wifi_bssid", wifiInfo.getBSSID());
            wifi.put("wifi_rssi", wifiInfo.getRssi());
        }
        wifi.put("enabled", wifiManager.isWifiEnabled());
        wifi.put("is_connected", mWiFiIsConnected);
        wifi.put("last_connected", mLastWiFiConnected);
        wifi.put("last_disconnected", mLastWiFiDisconnected);

        wifi.put("last_wifi_scan_results", mLastWiFiScanResults);
        JSONArray wifi_networks = new JSONArray();
        List<ScanResult> scanResults = wifiManager.getScanResults();
        if (scanResults != null) {
            for (ScanResult result: scanResults) {
                JSONObject network = new JSONObject();
                network.put("ssid", result.SSID);
                network.put("bssid", result.BSSID);
                network.put("dbmi", result.level);
            
                wifi_networks.add(network);
            }
        }
        wifi.put("scan_result", wifi_networks);
        state.put("wifi_info", wifi);
        
        JSONObject cell = new JSONObject();
        if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            GsmCellLocation location = (GsmCellLocation) telephonyManager.getCellLocation();
            if (location != null) {
                cell.put("type", "GSM");
                cell.put("LAC", location.getLac());
                cell.put("CID", location.getCid());
            }
        } else {
            CdmaCellLocation location = (CdmaCellLocation) telephonyManager.getCellLocation();
            if (location != null) {
                cell.put("type", "CDMA");
                cell.put("base_station_id", location.getBaseStationId());
                cell.put("base_station_latitude", location.getBaseStationLatitude());
                cell.put("base_station_longitude", location.getBaseStationLongitude());
                cell.put("network_id", location.getNetworkId());
                cell.put("system_id", location.getSystemId());
            }
        }
        JSONObject mobile = new JSONObject();
        mobile.put("cell_location", cell);
        mobile.put("rssi", mCurrentMobileRSSI);
        mobile.put("neighboring_cells", getNeighboringCellInfos(telephonyManager));
        state.put("mobile_info", mobile);
        
        state.put("last_data_activity", mLastDataActivity);
        
        return state;
    }
    
    @SuppressWarnings("unchecked")
    private JSONArray getNeighboringCellInfos(TelephonyManager telephonyManager) {
        JSONArray cells = new JSONArray();
        for (NeighboringCellInfo info: telephonyManager.getNeighboringCellInfo()) {
            JSONObject cell = new JSONObject();
            cell.put("rssi", Utils.translateGSMRssi(info.getRssi()));
            cell.put("network_type", info.getNetworkType());
            cell.put("LAC", info.getLac());
            cell.put("CID", info.getCid());
            cell.put("PSC", info.getPsc());
            cells.add(cell);
        }
        return cells;
    }
}
