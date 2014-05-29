package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import android.app.Activity;
import android.os.Bundle;

public class MapActivity extends Activity {
    
    private static final String sTag = MapActivity.class.getName();
    public static final String TAB_TAG = "MAP_TAB";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
}