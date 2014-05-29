package it.uniroma1.voiperf.util;

import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

public class NetworkStatusRecorder {

    private static final String sTag = NetworkStatusRecorder.class.getName();

    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;
    private final WifiManager mWifiManager;
    
    private boolean mIsStarted = false;
    private long mAllAppsTotalBytesReceived = 0;
    private long mAllAppsTotalBytesSent = 0;
    private long mMyAppTotalBytesReceived = 0;
    private long mMyAppTotalBytesSent = 0;

    public class RSSI {
        public final long timestamp;
        public final long value;
        public RSSI(long timestamp, long value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
    
    public class Status {
        public final long timestamp;
        public final NetworkInfo networkInfo;
        public final WifiInfo wifiInfo;
        public Status(long timestamp, NetworkInfo ninfo, WifiInfo winfo) {
            this.timestamp = timestamp;
            networkInfo = ninfo;
            wifiInfo = winfo;
        }
    }
    
    public class Cell {
        public final long timestamp;
        public final CellLocation cellLocation;
        public Cell(long timestamp, CellLocation cellLocation) {
            this.timestamp = timestamp;
            this.cellLocation = cellLocation;
        }
    }
    
    private ArrayList<RSSI> mMobileRSSIs;
    private ArrayList<RSSI> mWifiRSSIs;
    private ArrayList<Status> mStatuses;
    private ArrayList<Cell> mCells;
    
    private final PhoneStateListener mStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            long now = System.currentTimeMillis();
            super.onSignalStrengthsChanged(signalStrength);
            
            long rssi;
            if (signalStrength.isGsm()) {
                rssi = Utils.translateGSMRssi(signalStrength.getGsmSignalStrength());
            } else {
                rssi = signalStrength.getCdmaDbm();
            }
            mMobileRSSIs.add(new RSSI(now, rssi));
        }
        @Override
        public void onCellLocationChanged(CellLocation cellLocation) {
            mCells.add(new Cell(System.currentTimeMillis(), cellLocation));
        }
    };
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long now = System.currentTimeMillis();
            
            String action = intent.getAction();
            if (action == null) {
                return;
            } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                int rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
                mWifiRSSIs.add(new RSSI(now, rssi));
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo ninfo = mConnectivityManager.getActiveNetworkInfo();
                WifiInfo winfo = mWifiManager.getConnectionInfo();
                mStatuses.add(new Status(now, ninfo, winfo));
            }
        }
    };
    
    public NetworkStatusRecorder() {
        Context context = Session.getGlobalContext();
        mConnectivityManager = (ConnectivityManager) context
                                        .getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        
        mMobileRSSIs = new ArrayList<RSSI>();
        mWifiRSSIs = new ArrayList<RSSI>();
        mStatuses = new ArrayList<Status>();
        mCells = new ArrayList<Cell>();
    }
    
    public void startRecording() {
        if (mIsStarted) {
            return;
        }
        mIsStarted = true;
        
        // Save the total number of bytes sent/received before the test.
        mAllAppsTotalBytesReceived = TrafficStats.getTotalRxBytes();
        mAllAppsTotalBytesSent = TrafficStats.getTotalTxBytes();
        
        mMyAppTotalBytesReceived = TrafficStats.getUidRxBytes(
                Session.getGlobalContext().getApplicationInfo().uid);
        mMyAppTotalBytesSent = TrafficStats.getUidTxBytes(
                Session.getGlobalContext().getApplicationInfo().uid);
        
        Logger.i(sTag, "start recording");
        
        mTelephonyManager.listen(mStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        mTelephonyManager.listen(mStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        Session.getGlobalContext().registerReceiver(mReceiver, filter);
    }
    
    public void stopRecording() {
        if (mIsStarted == false) {
            return;
        }
        
        try {
            Logger.i(sTag, "stop recording");
            
            // Get the total number of bytes sent/received after the test
            long allAppsTotalBytesReceived = TrafficStats.getTotalRxBytes();
            long allAppsTotalBytesSent = TrafficStats.getTotalTxBytes();

            long myAppTotalBytesReceived = TrafficStats.getUidRxBytes(
                    Session.getGlobalContext().getApplicationInfo().uid);
            long myAppTotalBytesSent = TrafficStats.getUidTxBytes(
                    Session.getGlobalContext().getApplicationInfo().uid);
                
            mMyAppTotalBytesReceived = myAppTotalBytesReceived - mMyAppTotalBytesReceived;
            mMyAppTotalBytesSent = myAppTotalBytesSent - mMyAppTotalBytesSent;
            mAllAppsTotalBytesReceived = allAppsTotalBytesReceived - mAllAppsTotalBytesReceived;
            mAllAppsTotalBytesSent = allAppsTotalBytesSent - mAllAppsTotalBytesSent;
            
            Logger.d(sTag, "All sent: " + mAllAppsTotalBytesSent + " All received: " + mAllAppsTotalBytesReceived);
            Logger.d(sTag, "VoIPerf sent: " + mMyAppTotalBytesSent + " VoIPerf received: " + mMyAppTotalBytesReceived);
            
            mTelephonyManager.listen(mStateListener, PhoneStateListener.LISTEN_NONE);
            Session.getGlobalContext().unregisterReceiver(mReceiver);
        } finally {
            mIsStarted = false;
        }       
    }
    
    public List<RSSI> getWifiRSSIs() {
        return mWifiRSSIs;
    }
    
    public List<RSSI> getMobileRSSIs() {
        return mMobileRSSIs;
    }
    
    public List<Status> getStatuses() {
        return mStatuses;
    }
    
    public List<Cell> getCells() {
        return mCells;
    }
    
    @SuppressWarnings("unchecked")
    public JSONObject getResult() {
        JSONObject result = new JSONObject();
        
        JSONArray wifiRSSIs = new JSONArray();
        for (RSSI r: mWifiRSSIs) {
            JSONObject rssi = new JSONObject();
            rssi.put("timestamp", Utils.ms_to_s(r.timestamp));
            rssi.put("rssi", r.value);
            wifiRSSIs.add(rssi);
        }
        result.put("wifi_rssis", wifiRSSIs);
        
        JSONArray mobileRSSIs = new JSONArray();
        for (RSSI r: mMobileRSSIs) {
            JSONObject rssi = new JSONObject();
            rssi.put("timestamp", Utils.ms_to_s(r.timestamp));
            rssi.put("rssi", r.value);
            mobileRSSIs.add(rssi);
        }
        result.put("mobile_rssis", mobileRSSIs);
        
        JSONArray cells = new JSONArray();
        for (Cell c: mCells) {
            // TODO: this is copied from NetworkStatus. Refactor
            JSONObject cell = new JSONObject();
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                GsmCellLocation location = (GsmCellLocation) mTelephonyManager.getCellLocation();
                if (location != null) {
                    cell.put("type", "GSM");
                    cell.put("LAC", location.getLac());
                    cell.put("CID", location.getCid());
                }
            } else {
                CdmaCellLocation location = (CdmaCellLocation) mTelephonyManager.getCellLocation();
                if (location != null) {
                    cell.put("type", "CDMA");
                    cell.put("base_station_id", location.getBaseStationId());
                    cell.put("base_station_latitude", location.getBaseStationLatitude());
                    cell.put("base_station_longitude", location.getBaseStationLongitude());
                    cell.put("network_id", location.getNetworkId());
                    cell.put("system_id", location.getSystemId());
                }
            }
            cells.add(cell);
        }
        result.put("cell_locations", cells);
        
        JSONArray statuses = new JSONArray();
        for (Status s: mStatuses) {
            JSONObject status = new JSONObject();
            status.put("timestamp", Utils.ms_to_s(s.timestamp));
            
            JSONObject wifi = new JSONObject();
            if (s.wifiInfo != null) {
                wifi.put("ssid", s.wifiInfo.getSSID());
                wifi.put("bssid", s.wifiInfo.getBSSID());
                wifi.put("rssi", s.wifiInfo.getRssi());
                wifi.put("link_speed", s.wifiInfo.getLinkSpeed());
            } else {
                wifi.put("ssid", null);
                wifi.put("bssid", null);
                wifi.put("rssi", null);
                wifi.put("link_speed", null);
            }
            status.put("wifi", wifi);
            
            if (s.networkInfo != null) {
                status.put("connection_type", s.networkInfo.getTypeName());
                status.put("connection_subtype", s.networkInfo.getSubtypeName());
                status.put("is_connected", s.networkInfo.isConnected());
                status.put("is_available", s.networkInfo.isAvailable());
            } else {
                status.put("connection_type", null);
                status.put("connection_subtype", null);
                status.put("is_connected", null);
                status.put("is_available", null);
            }
        }
        result.put("statuses", statuses);
        
        JSONObject trafficStats = new JSONObject();
        trafficStats.put("voiperf_bytes_sent", mMyAppTotalBytesSent);
        trafficStats.put("voiperf_bytes_received", mMyAppTotalBytesReceived);
        trafficStats.put("all_apps_bytes_sent", mAllAppsTotalBytesSent);
        trafficStats.put("all_apps_bytes_received", mAllAppsTotalBytesReceived);
        result.put("traffic_stats", trafficStats);
        
        return result;
    }
}
