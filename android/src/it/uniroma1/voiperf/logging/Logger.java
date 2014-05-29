package it.uniroma1.voiperf.logging;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.Session;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Wrapper for logging operations which can be disabled by setting LOGGING_ENABLED to false.
 */
public class Logger {
    
  private final static boolean LOGGING_ENABLED = true;
  private static FileLogger sFileLogger = null;
  
  private static void initFileLogger() {
      if (sFileLogger != null) {
          return;
      }
      
      Context context = Session.getGlobalContext();
      if (context != null) {
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
          long maxSz = prefs.getInt(Config.SYSTEM_LOG_ROTATE_SZ_KEY, Config.SYSTEM_LOG_ROTATE_SZ_DEFAULT);
          int rotationSeconds = Config.SYSTEM_LOG_ROTATE_SECONDS_DEFAULT;
          long lastRotationTime = prefs.getLong(Config.LAST_SYSTEM_LOG_FILE_ROTATION_KEY, 0);
          
          sFileLogger = new FileLogger("system", "log", maxSz, rotationSeconds, lastRotationTime);
      }
  }
  
  private static void writeln(String message) {
      initFileLogger();
      if (sFileLogger != null) {
          sFileLogger.write(message + "\n");
      }
  }
  
  public static void rotateIfNecessary() {
      initFileLogger();
      if (sFileLogger.rotateIfNecessary()) {
          long now = System.currentTimeMillis();
          Context context = Session.getGlobalContext();
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
          prefs.edit().putLong(Config.LAST_SYSTEM_LOG_FILE_ROTATION_KEY, now).commit();
      }
  }
  
  public static void d(String tag, String msg) {
    if (LOGGING_ENABLED) {
      Log.d(tag, msg);
      writeln("DEBUG " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void d(String tag, String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.d(tag, msg, t);
      writeln("DEBUG " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void e(String tag, String msg) {
    if (LOGGING_ENABLED) {
      Log.e(tag, msg);
      writeln("ERROR " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void e(String tag, String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.e(tag, msg, t);
      writeln("ERROR " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void i(String tag, String msg) {
    if (LOGGING_ENABLED) {
      Log.i(tag, msg);
      writeln("INFO " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void i(String tag, String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.i(tag, msg, t);
      writeln("INFO " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void v(String tag, String msg) {
    if (LOGGING_ENABLED) {
      Log.v(tag, msg);
      writeln("VERBOSE " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void v(String tag, String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.v(tag, msg, t);
      writeln("VERBOSE " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void w(String tag, String msg) {
    if (LOGGING_ENABLED) {
      Log.w(tag, msg);
      writeln("WARNING " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }
  
  public static void w(String tag, String msg, Throwable t) {
    if (LOGGING_ENABLED) {
      Log.w(tag, msg, t);
      writeln("WARNING " + tag + " " + System.currentTimeMillis() + " " + msg);
    }
  }

}