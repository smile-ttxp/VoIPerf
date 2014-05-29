package it.uniroma1.voiperf.results;

import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;

public class ResultsDatabase {
    
    private static final String sTag = ResultsDatabase.class.getName();
    
    private static final String sDatabaseName = "voiperfDB";
    private static final int sDatabaseVersion = 2;
    private static final String sResultTimestamp = "timestamp";
    private static final String sResultType = "type";
    private static final String sResultRate = "rate";
    private static final String sResultPacketLoss = "packet_loss";
    private static final String sResultAverageJitter = "average_jitter";
    private static final String sResultMOS = "MOS";
    private static final String sVoiperfResultsTable = "results_summary";
   
    private final ResultsDatabaseHelper mDatabaseOpenHelper;
    private static final HashMap<String,String> mColumnMap = buildColumnMap();
    
    public ResultsDatabase(Context context) {
        if (context == null) {
            Logger.d(sTag, "null context in constructor");
        }
        mDatabaseOpenHelper = new ResultsDatabaseHelper(context);
    }
      
    public ArrayList<Result> getLastResults(int count) {
        Logger.d(sTag, "Getting last results");
        ArrayList<Result> results = new ArrayList<Result>();
        long now = System.currentTimeMillis();
        
        // Get results for the last 3 days.    
        long oldTimestamp = (now / 1000) - (3 * 24 * 60 * 60);
        String selection = "timestamp > " + oldTimestamp;
        String sortOrder = "timestamp DESC";
        
        Cursor cursor = query(null, selection, null, null, null, sortOrder);
        if (cursor == null) {
            Logger.d(sTag, "Cursor is null!");
        } else {
            if (count > Session.MAX_RESULTS_VIEW) {
                count = Session.MAX_RESULTS_VIEW;
            }
            cursor.moveToFirst();
            results.add(fromCursor(cursor));
            while (cursor.moveToNext() && count-- > 0) {
                results.add(fromCursor(cursor));
            }
        }
        return results;
    }
    
    public Result fromCursor(Cursor cursor) {
        Result result = new Result();
        result.setTimestamp(cursor.getLong(cursor.getColumnIndex(sResultTimestamp)));
        result.setType(cursor.getString(cursor.getColumnIndex(sResultType)));
        result.setRate(cursor.getDouble(cursor.getColumnIndex(sResultRate)));
        result.setPacketLoss(cursor.getDouble(cursor.getColumnIndex(sResultPacketLoss)));
        result.setAverageJitter(cursor.getDouble(cursor.getColumnIndex(sResultAverageJitter)));
        result.setMOS(cursor.getDouble(cursor.getColumnIndex(sResultMOS)));
        
        return result;
    }
    
    public synchronized Boolean insertResult(Result result) {
        SQLiteDatabase db = mDatabaseOpenHelper.getWritableDatabase();
        if (db == null) {
            Logger.d(sTag, "Could not open database for writing");
            return false;
        } 
        ContentValues initialValues = new ContentValues();
        initialValues.put(sResultTimestamp, result.getTimestamp());
        initialValues.put(sResultType, result.getType());
        initialValues.put(sResultRate, result.getRate());
        initialValues.put(sResultPacketLoss, result.getPacketLoss() );
        initialValues.put(sResultAverageJitter, result.getAverageJitter());
        initialValues.put(sResultMOS, result.getMOS());
        
        long position =  db.insert(sVoiperfResultsTable, null, initialValues);
        if (position == -1) {
            Logger.d(sTag, "Could not insert result");
            return false;
        }
        Logger.d(sTag, "Result inserted at position " + position);
        return true;
    }
    
    /**
    * Builds a map for all columns that may be requested, which will be given to the
    * SQLiteQueryBuilder. This is a good way to define aliases for column names, but must include
    * all columns, even if the value is the key. This allows the ContentProvider to request
    * columns w/o the need to know real column names and create the alias itself.
    */
    private static HashMap<String,String> buildColumnMap() {
        HashMap<String,String> map = new HashMap<String,String>();
        map.put(sResultTimestamp, sResultTimestamp);
        map.put(sResultType, sResultType);
        map.put(sResultRate, sResultRate);
        map.put(sResultPacketLoss, sResultPacketLoss);
        map.put(sResultAverageJitter, sResultAverageJitter);
        map.put(sResultMOS, sResultMOS);
        map.put(BaseColumns._ID, "rowid AS " +
                BaseColumns._ID);
        map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, "rowid AS " +
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
        map.put(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID, "rowid AS " +
                SearchManager.SUGGEST_COLUMN_SHORTCUT_ID);
        return map;
    }
    
    /**
    * Performs a database query.
    * @param selection The selection clause
    * @param selectionArgs Selection arguments for "?" components in the selection
    * @param columns The columns to return
    * @return A Cursor over all rows matching the query
    */
    private Cursor query(String[] projectionIn, String selection, String selectionArgs[], 
                                    String[] groupBy, String having, String sortOrder) {
        /* The SQLiteBuilder provides a map for all possible columns requested to
        * actual columns in the database, creating a simple column alias mechanism
        * by which the ContentProvider does not need to know the real column names
        */
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(sVoiperfResultsTable);
        builder.setProjectionMap(mColumnMap);

        Cursor cursor = builder.query(mDatabaseOpenHelper.getReadableDatabase(),
                projectionIn, selection, groupBy, having, null, sortOrder);

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private class ResultsDatabaseHelper extends SQLiteOpenHelper {
        
        private static final String sVoiperfResultsTableCreate =
                                      "CREATE TABLE IF NOT EXISTS " + sVoiperfResultsTable + " (" +
                                      sResultTimestamp + " INT(11), " + sResultType + " TEXT, "   +
                                      sResultRate + " DOUBLE, " + sResultPacketLoss + " DOUBLE, " +
                                      sResultAverageJitter + " DOUBLE, " + sResultMOS + " DOUBLE);";
        
        ResultsDatabaseHelper(Context context) {
            super(context, sDatabaseName, null, sDatabaseVersion);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(sVoiperfResultsTableCreate);
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Logger.w(sTag, "Upgrading database from version " + oldVersion + " to "
                            + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + sVoiperfResultsTable);
            onCreate(db);
        }
    }
}