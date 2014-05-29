package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.schedulers.Schedulers;
import it.uniroma1.voiperf.util.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

import com.bugsense.trace.BugSenseHandler;

import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;

@SuppressWarnings("deprecation")
public class MainActivity extends TabActivity implements MeasurementServiceClient {

    private static final String sTag = MainActivity.class.getName();
    private static Intent sServiceIntent;
    
    private TabHost mTabHost;
    
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            Session.sMeasurementService = null;
        }
       
        public void onServiceConnected(ComponentName name, IBinder service) {
            Logger.d(sTag, "onServiceConnected");
            Session.sMeasurementService = ((MeasurementService.MeasurementBinder) service).getService();
            Session.setBoundToService(true);
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d(sTag, "onCreate");
        super.onCreate(savedInstanceState);
        Session.setGlobalContext(getApplicationContext());
        
        BugSenseHandler.initAndStartSession(getApplicationContext(), "73495b83");
       
        setContentView(R.layout.activity_main);
        try {
            checkThirdPartyBinaries();
            checkTraces();
        } catch (Exception e) {
            Logger.e(sTag, "FATAL ERROR: failed to copy app assets", e);
            // TODO: alert the user and die gracefully?
        }
        
        Resources res = getResources(); // Resource object to get Drawables
        mTabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;    // Reusable TabSpec for each tab
        Intent intent;           // Reusable Intent for each tab

        intent = new Intent().setClass(this, StatusActivity.class);
        spec = mTabHost.newTabSpec(StatusActivity.TAB_TAG).setIndicator(
            "Status", res.getDrawable(R.drawable.ic_tab_status)).setContent(intent);
        mTabHost.addTab(spec);

        intent = new Intent().setClass(this, ResultsActivity.class);
        spec = mTabHost.newTabSpec(ResultsActivity.TAB_TAG).setIndicator(
            "Results", res.getDrawable(R.drawable.ic_tab_results)).setContent(intent);
        mTabHost.addTab(spec);
       
        /*
        intent = new Intent().setClass(this, MapActivity.class);
        spec = tabHost.newTabSpec(MapActivity.TAB_TAG).setIndicator(
            "Map", res.getDrawable(R.drawable.ic_tab_map)).setContent(intent);
        tabHost.addTab(spec);
        */
 
        mTabHost.setCurrentTabByTag(StatusActivity.TAB_TAG);
    }

    @Override
    protected void onStart() {
        Logger.d(sTag, "onStart");
        super.onStart();
        startAndBindService();
    }

    protected void onStop() {
        Logger.d(sTag, "onStop");
        super.onStop();
        stopAndUnbindService();
    }

    @Override
    protected void onResume() {
        Logger.d(sTag, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Logger.d(sTag, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Logger.d(sTag, "onDestroy");
        super.onDestroy();
    }

    private void startAndBindService() {
        sServiceIntent = new Intent(this, MeasurementService.class);
        startService(sServiceIntent);
        if (bindService(sServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE) == false) {
            Logger.e(sTag, "BUG: bindService failed!");
        } else {
            Session.setBoundToService(true);
        }
    }

    private void stopAndUnbindService() {
        if (Session.isBoundToService()) {
            unbindService(mServiceConnection);
            Session.setBoundToService(false);
        } else {
            Logger.w(sTag, "BUG: Stopping the service, but it was not bounded! (not critical)");
        }
        if (Session.isForegroundServiceRunning() == false) {
            stopService(sServiceIntent);
        }
    }

    private void updateStatus() {
        Logger.d(sTag, "updateStatus");
        sendBroadcast(new Intent(Schedulers.STATUS_UPDATE_ACTION));
    }
    
    @Override
    public void onBackgroundServiceStarted() {
        Logger.d(sTag, "onBackgroundServiceStarted");
    }

    @Override
    public void onBackgroundServiceStopped() {
        Logger.d(sTag, "onBackgroundServiceStopped");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuSettings:
                startActivity(new Intent(getBaseContext(), VoIPerfPreferenceActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkThirdPartyBinaries() throws Exception {
        Context context = Session.getGlobalContext();
        for (String binaryFileName: getAssets().list("bin")) {
            File binaryFile = context.getFileStreamPath(binaryFileName);
            if (binaryFile.exists() == false) {
                Logger.d(sTag, "Start copying binary " + binaryFileName);
                BufferedOutputStream os = null;
                BufferedInputStream is = null;
                try {
                    is = new BufferedInputStream(getAssets().open("bin/" + binaryFileName));
                    os = new BufferedOutputStream(context.openFileOutput(binaryFileName,
                                                                         MODE_PRIVATE));
                    Utils.copy(is, os);
                    Utils.setFileExecutable(binaryFile.getAbsolutePath());
                    Logger.d(sTag, "...done!");
                } catch (Exception e) {
                    Logger.d(sTag, "Error while copying binary " + binaryFileName);
                    Logger.d(sTag, "Trace: " + e.getMessage());
                    throw e;
                } finally {
                    if (is != null) is.close();
                    if (os != null) os.close();
                }
            }
        }
    }
    
    private void checkTraces() throws Exception {
        Context context = Session.getGlobalContext();
        for (String traceFileName: getAssets().list("traces")) {
            File binaryFile = context.getFileStreamPath(traceFileName);
            if (binaryFile.exists() == false) {
                Logger.d(sTag, "Start copying trace " + binaryFile);
                BufferedOutputStream os = null;
                BufferedInputStream is = null;
                try {
                    is = new BufferedInputStream(getAssets().open("traces/" + traceFileName));
                    os = new BufferedOutputStream(context.openFileOutput(traceFileName,
                                                                         MODE_PRIVATE));
                    Utils.copy(is, os);
                    Logger.d(sTag, "...done!");
                } catch (Exception e) {
                    Logger.d(sTag, "Error while copying trace " + binaryFile);
                    Logger.d(sTag, "Trace: " + e.getMessage());
                    throw e;
                } finally {
                    if (is != null) is.close();
                    if (os != null) os.close();
                }
            }
        }
    }
}
