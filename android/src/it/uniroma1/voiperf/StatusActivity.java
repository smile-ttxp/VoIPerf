package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.ToggleButton;

public class StatusActivity extends Activity implements OnCheckedChangeListener {

    public static final String TAB_TAG = "STATUS_TAB";

    private static final String sTag = StatusActivity.class.getName();

    private ListView mStatusView;
    private ToggleButton mOnOffButton;
    private SimpleAdapter mStatus;
    
    private final BroadcastReceiver mUpdateActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
            mOnOffButton.setChecked(Session.isForegroundServiceRunning());
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Session.setGlobalContext(getApplicationContext());
        this.setContentView(R.layout.status);
        
        mOnOffButton = (ToggleButton) findViewById(R.id.buttonOnOff);
        mOnOffButton.setClickable(true);
        mOnOffButton.setChecked(Session.isForegroundServiceRunning());
        mOnOffButton.setOnCheckedChangeListener(StatusActivity.this);
        
        registerReceiver(mUpdateActionReceiver, new IntentFilter(Schedulers.STATUS_UPDATE_ACTION));
        updateStatus();
    }
    
    @Override
    protected void onResume() {
        Logger.d(sTag, "onResume");
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onPause() {
        Logger.d(sTag, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Logger.d(sTag, "onDestroy");
        unregisterReceiver(mUpdateActionReceiver);
        super.onDestroy();
    }
    
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Logger.d(sTag, "onCheckedChanged");
        if (isChecked) {
            Session.sMeasurementService.start();
            Session.setBoundToService(true);
        }
        else {
            Session.sMeasurementService.stop();
            Session.setBoundToService(false);
        }
        updateStatus();
    }
    
    private synchronized void updateStatus() { 
        Logger.d(sTag, "updateStatus");
        //checkVoIPerfUpdate();
        
        mStatusView = (ListView) findViewById(R.id.statusView);
        ArrayList<HashMap<String, String>> statusData = new ArrayList<HashMap<String, String>>();

        String[] columnTags = new String[] {"first", "second"};
        int[] columnIds = new int[] {R.id.column1, R.id.column2};
        
        String status = Session.getStatus();
        String nextMeasurement = Session.getNextMeasurementDate();
        if (nextMeasurement.equals("")) {
            nextMeasurement = "-";
        }
        
        HashMap<String,String> map = new HashMap<String, String>();
        map.put("first", "Current status");
        map.put("second", Session.getStatus());
        statusData.add(map);
        
        if (status.equals("Running")) {     
            map = new HashMap<String, String>();
            map.put("first", "Next measurement");
            map.put("second", nextMeasurement);
            statusData.add(map);
        }
        
        mStatus = new SimpleAdapter(this, statusData, R.layout.status_view, columnTags , columnIds);
        mStatusView.setAdapter(mStatus);
          
        runOnUiThread(new Runnable() {
          public void run() { mStatus.notifyDataSetChanged(); }
        });
    }
    
    private void checkVoIPerfUpdate() {
        if (Session.checkVoIPerfUpdate()) {
            Session.getUpdateManager().checkUpdate(this);
        }
    }
}
