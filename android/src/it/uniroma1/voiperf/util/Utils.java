package it.uniroma1.voiperf.util;

import it.uniroma1.voiperf.R;
import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.NeighboringCellInfo;

public class Utils {
    
    private static final String sTag = Utils.class.getName();
    
    public static String getTimestamp() {
        String dateTimeString = new String("yyyy-MM-dd_HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat(dateTimeString, Locale.US);
        String timestamp = sdf.format(new Date());
        
        return timestamp;
    }

    public static String getFileTimestamp() {
        String dateTimeString = new String("yyyy-MM-dd_HH-mm-ss");
        SimpleDateFormat sdf = new SimpleDateFormat(dateTimeString, Locale.US);
        String timestamp = sdf.format(new Date());
        
        return timestamp;
    }
    
    public static void setFileExecutable(String path) throws Exception {
        String command = "chmod 744 " + path;
        Process p = Runtime.getRuntime().exec(command);
        int ret = p.waitFor();
        if (ret != 0) {
            throw new Exception("Command: " + command + " failed with return value " + ret);
        }
    }
    
    public static void copy(File src, File dst) throws IOException {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(src));
            bos = new BufferedOutputStream(new FileOutputStream(dst));
            copy(bis, bos);
        } finally {
            if (bis != null) bis.close();
            if (bos != null) bos.close();
        }
    }
    
    public static void copy(InputStream src, OutputStream dst) throws IOException {
        byte[] buff = new byte[256 * 1024];
        int nread;
        while ((nread = src.read(buff)) != -1) {
            dst.write(buff, 0, nread);
        }
    }
    
    public static void copy(File src, OutputStream dst) throws IOException {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(src));
            copy(bis, dst);
        } finally {
            if (bis != null) bis.close();
        }
    }
    
    public static void copy(InputStream src, File dst) throws IOException {
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(dst));
            copy(src, bos);
        } finally {
            if (bos != null) bos.close();
        }
    }
    
    public static void compressFile(File src, File dst) throws IOException {
        BufferedInputStream bis = null;
        GZIPOutputStream gzos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(src));

            gzos = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(dst)));
        
            byte[] buff = new byte[256 * 1024];
            int nread;
            while ((nread = bis.read(buff)) != -1) {
                gzos.write(buff, 0, nread);
            }
        } finally {
            if (bis != null) bis.close();
            if (gzos != null) gzos.close();
        }
    }
    
    public static byte[] compressString(String src) throws IOException {
        InputStream srcStream = new ByteArrayInputStream(src.getBytes());
        ByteArrayOutputStream dstStream = new ByteArrayOutputStream();
        GZIPOutputStream gzos = null;
        try {
            gzos = new GZIPOutputStream(dstStream);

            byte[] buff = new byte[256 * 1024];
            int nread;
            while ((nread = srcStream.read(buff)) != -1) {
                gzos.write(buff, 0, nread);
            }
            
            gzos.close();
            gzos = null;
            
            return dstStream.toByteArray();
        } finally {
            srcStream.close();
            dstStream.close();
            if (gzos != null) gzos.close();
        }
    }
    
    public static void sendCompressedJSON(DataOutputStream dos, JSONObject obj)
        throws IOException {
        byte[] result = compressString(obj.toJSONString());
        dos.writeBytes(result.length + "\n");
        dos.write(result);
    }
    
    @SuppressWarnings("unchecked")
    public static JSONArray toJSONArray(long[] values) {
        JSONArray array = new JSONArray();
        for (long val: values) {
            array.add(val);
        }
        return array;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> JSONArray toJSONArray(List<T> values) {
        JSONArray array = new JSONArray();
        for (T val: values) {
            array.add(val);
        }
        return array;
    }
    
    public static boolean isSDCardWriteable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return false;
        } else {
            return false;
        }
    }
    
    public static File getExternalFilesDir() {
        Context context = Session.getGlobalContext();
        String sdcard = Environment.getExternalStorageDirectory().getAbsolutePath();
        File filesDir = new File(sdcard + "/" + context.getString(R.string.externalFilesDir));
        filesDir.mkdirs();
        return filesDir;
    }
    
    public static File moveFileToSDCard(File file) throws IOException {
        if (isSDCardWriteable() == false)
            throw new IOException("External files directory is not writeable");
        File filesDir = getExternalFilesDir();
        File output = new File(filesDir.getAbsolutePath() + "/" + file.getName());
        copy(file, output);
        file.delete();
        return output;
    }
    
    // See: http://andy-malakov.blogspot.it/2010/06/alternative-to-threadsleep.html
    private static final long sSleepPrecision = TimeUnit.MILLISECONDS.toNanos(1);    // TODO: tune
    private static final long sSpinYieldPrecision = TimeUnit.NANOSECONDS.toNanos(1); // TODO: tune
    
    public static void sleepMillisBusyWait(long millisDuration) {
        long start = System.nanoTime();
        long now = start;
        while (TimeUnit.NANOSECONDS.toMillis(now - start) < millisDuration) {
            now = System.nanoTime();
        }
    }
    
    public static void sleepMillis(long millisDuration) throws InterruptedException {
        sleepNanos(TimeUnit.MILLISECONDS.toNanos(millisDuration));
    }
    
    public static void sleepMillisWithSpin(long millisDuration) throws InterruptedException {
        sleepNanosWithSpin(TimeUnit.MILLISECONDS.toNanos(millisDuration));
    }
    
    public static void sleepNanos(long nanoDuration) throws InterruptedException {
        final long end = System.nanoTime() + nanoDuration;
        long timeLeft = nanoDuration;
        do {
            if (timeLeft > sSleepPrecision) {
                Thread.sleep(1);
            } else {
                // Equivalent to Thread.yield()
                Thread.sleep(0);
            }
            timeLeft = end - System.nanoTime();
            if (Thread.interrupted())
                throw new InterruptedException();
        } while (timeLeft > 0);
    }
    
    public static void sleepNanosWithSpin(long nanoDuration) throws InterruptedException { 
        final long end = System.nanoTime() + nanoDuration;
        long timeLeft = nanoDuration; 
        do { 
            if (timeLeft > sSleepPrecision) {
                Thread.sleep(1); 
            }
            else if (timeLeft > sSpinYieldPrecision) {
                // Equivalent to Thread.yield()
                Thread.sleep(0);
            }
            timeLeft = end - System.nanoTime(); 
            if (Thread.interrupted()) 
                throw new InterruptedException(); 
        } while (timeLeft > 0); 
    }
    
    private static WakeLock mWakeLock = null;
    
    public static synchronized void acquireWakeLock() {
      if (mWakeLock == null) {
        PowerManager pm = (PowerManager) Session.getGlobalContext()
                                        .getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tag");
      }
      mWakeLock.acquire();
    }

    public static synchronized void releaseWakeLock() {
      if (mWakeLock != null) {
        try {
          mWakeLock.release();
        } catch (RuntimeException e) {
          Logger.e(sTag, "Exception when releasing wakeup lock", e);
        }
      }
    }

    protected static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public static double average(long[] values) {
        
        if (values.length == 0) {
            return 0.0;
        }
        
        double av = 0;
        for (long v: values) {
            av += v;
        }
        av = av / values.length;
        return av;
    }
    
    public static double variance(long[] values) {
        
        if (values.length == 0)
            return 0.0;
        
        double av = average(values);
        double var = 0.0;
        for (long v: values) {
            var += (v - av) * (v - av);
        }
        var = Math.sqrt(var / values.length);
        return var;
    }
    
    public static long[] quartiles(long[] values) {
        
        long[] q = new long[5];
        if (values.length == 0) {
            return q;
        }

        Arrays.sort(values);
        q[0] = values[0];
        q[1] = values[(int) (values.length * 0.25)];
        q[2] = values[(int) (values.length * 0.5)];
        q[3] = values[(int) (values.length * 0.75)];
        q[4] = values[values.length - 1];
        
        return q;
    }
    
    public static double ms_to_s(long millis) {
        return millis / 1000.0;
    }
    
    public static int translateGSMRssi(int rssi) {
        if (rssi != NeighboringCellInfo.UNKNOWN_RSSI) {
            return -113 + 2 * rssi;
        }
        return rssi;
    }
    
    @SuppressWarnings("unchecked")
    public static JSONObject dumpPreferences() {
        
        JSONObject dump = new JSONObject();
        
        Context context = Session.getGlobalContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs != null) {
            for (Map.Entry<String,?> entry: prefs.getAll().entrySet()) {
                dump.put(entry.getKey(), entry.getValue());
            }
        }
        return dump;
    }
    
    @SuppressLint("SimpleDateFormat")
	public static String timestampToString(long timestamp) {
        if (timestamp == 0) {
            return "";
        }
        Date d = new Date(timestamp * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM, HH:mm");
        return sdf.format(d);
    }
}
