package it.uniroma1.voiperf.logging;

import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.util.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import android.content.Context;

public class FileLogger {
    
    private final String mExtension;
    private final String mPrefix;
    private final long mMaxSize;
    
    private final long mRotationMillis;
    private long mLastRotationTime;
    
    private File mLogFile;
    
    public FileLogger(String prefix, String extension, long maxSize,
                      long rotationSeconds, long lastRotationTime) {
        if (prefix != null) {
            mPrefix = prefix;
        } else {
            mPrefix = "";
        }
        if (extension != null) {
            mExtension = extension;
        } else {
            mExtension = "log";
        }
        mMaxSize = maxSize;
        mRotationMillis = TimeUnit.SECONDS.toMillis(rotationSeconds);
        if (lastRotationTime > 0) {
            mLastRotationTime = lastRotationTime;
        } else {
            mLastRotationTime = System.currentTimeMillis();
        }
    }
    
    public FileLogger(String prefix, String extension, long maxSize, long rotationSeconds) {
        this(prefix, extension, maxSize, rotationSeconds, 0);
    }
    
    public synchronized void write(String message) {
        try {
            if (mLogFile == null) {
                createNewLogFile();
            }
        
            BufferedOutputStream out = new BufferedOutputStream(
                                            new FileOutputStream(mLogFile, true));
            out.write(message.getBytes());
            out.close();
        } catch (Exception e) {
        }
    }
    
    public synchronized boolean shouldRotate() {
        if (mMaxSize < mLogFile.length()) {
            return true;
        } else if (mRotationMillis < (System.currentTimeMillis() - mLastRotationTime)) {
            return true;
        }
        return false;
    }
    
    public synchronized boolean rotateIfNecessary() {
        if (shouldRotate() == false) {
            return false;
        }
        rotate();
        return true;
    }
    
    public synchronized void rotate() {
        closeCurrentLogFile();
        compressLogFiles();
        moveCompressedLogFilesToSDCard();
        mLastRotationTime = System.currentTimeMillis();
    }
    
    private void createNewLogFile() throws Exception {
        try {
            Context context = Session.getGlobalContext();
            mLogFile = context.getFileStreamPath(buildFileName());
            if (mLogFile.createNewFile()) {
            } else {
            }
        } catch (Exception e) {
            throw e;
        }
    }
    
    private String buildFileName() {
        String filename = "";
        if (mPrefix != null) {
            filename += mPrefix + "-";
        }
        filename += Utils.getFileTimestamp() + "." + mExtension;
        return filename;
    }
    
    private boolean isLogFile(String filename) {
        if (mPrefix != null) {
            if (filename.startsWith(mPrefix + "-") == false) {
                return false;
            }
        }
        return filename.endsWith("." + mExtension);
    }
    
    private boolean isCompressedLogFile(String filename) {
        if (mPrefix != null) {
            if (filename.startsWith(mPrefix + "-") == false) {
                return false;
            }
        }
        return filename.endsWith("." + mExtension + ".gz");
    }
    
    private void compressLogFiles() {
                
        FileFilter logFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return isLogFile(pathname.getName());
            }
        };
        
        Context context = Session.getGlobalContext();
        File root = context.getFilesDir();
        for (File logFile: root.listFiles(logFilter)) {
            try {
                String srcName = logFile.getName();
                File gzLogFile = context.getFileStreamPath(srcName + ".gz");
                Utils.compressFile(logFile, gzLogFile);
                logFile.delete();
            } catch (Exception e) {
            }
        }
    }
    
    private void closeCurrentLogFile() {
        mLogFile = null;
    }
    
    private void moveCompressedLogFilesToSDCard() {
        
        FileFilter gzFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return isCompressedLogFile(pathname.getName());
            }
        };
        
        File root = Session.getGlobalContext().getFilesDir();
        for (File gzFile: root.listFiles(gzFilter)) {
            try {
                Utils.moveFileToSDCard(gzFile).getAbsolutePath();
            } catch (Exception e) {
            }
        }
    }
}
