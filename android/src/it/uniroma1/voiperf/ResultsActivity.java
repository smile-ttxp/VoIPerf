package it.uniroma1.voiperf;

import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.results.Result;
import it.uniroma1.voiperf.util.Utils;

import java.util.ArrayList;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;

public class ResultsActivity extends Activity {

    public static final String TAB_TAG = "RESULTS_TAB";    
    private static final String sTag = ResultsActivity.class.getName();
    private TableLayout mTableLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {     
        super.onCreate(savedInstanceState);
        Session.setGlobalContext(getApplicationContext());
        this.setContentView(R.layout.results_view);
        showResults();
    }
    
    @Override
    protected void onResume() {
        Logger.d(sTag, "onResume");
        super.onResume();
        showResults();
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
    
    private void showResults() {
        Logger.d(sTag, "showResults");
        //checkVoIPerfUpdate();
        clearResults();
        
        mTableLayout = (TableLayout) ResultsActivity.this.findViewById(R.id.resultsTable);
        LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, 
                                        LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
                 
        int [] backgroundColors  = { Color.DKGRAY, Color.GRAY };       
        int colorIndex = 0;
        int textColor = Color.YELLOW;
        
        ArrayList<Result> results = Session.getDatabaseHandler().getLastResults(Session.MAX_RESULTS_VIEW);        
        for (Result result : results) {
            TableRow row = new TableRow(this);
            row.setLayoutParams(lp);
            row.addView(resultColumn(Utils.timestampToString(result.getTimestamp()),
                                            backgroundColors[colorIndex], textColor, lp));
            row.addView(resultColumn(result.getType(), 
                                            backgroundColors[colorIndex], textColor, lp));
            row.addView(resultColumn("" + result.getRate(), 
                                            backgroundColors[colorIndex], textColor, lp));
            row.addView(resultColumn("" + result.getPacketLoss(), 
                                            backgroundColors[colorIndex], textColor, lp));
            row.addView(resultColumn("" + result.getMOS(), 
                                            backgroundColors[colorIndex], textColor, lp)); 
            mTableLayout.addView(row, new TableLayout.LayoutParams(LayoutParams.FILL_PARENT, 
                                            LayoutParams.WRAP_CONTENT));
            colorIndex = (colorIndex + 1) % 2;
        }   
    }
    
    private TextView resultColumn(String content, int backgroundColor,
                                  int textColor,  LayoutParams params) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(params);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setBackgroundColor(backgroundColor);
        tv.setTextColor(textColor);
        tv.setText(content);
        tv.setPadding(0, 5, 0, 5);
        return tv;
    }
    
    private void clearResults() {
        mTableLayout = (TableLayout) ResultsActivity.this.findViewById(R.id.resultsTable);
        mTableLayout.removeAllViews();
    }
    
    private void checkVoIPerfUpdate() {
        if (Session.checkVoIPerfUpdate()) {
            Session.getUpdateManager().checkUpdate(this);
        }
    }
}
