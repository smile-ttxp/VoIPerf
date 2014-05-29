package it.uniroma1.voiperf.traces;

import it.uniroma1.voiperf.logging.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public abstract class Trace {
    
    private static final String sTag = Trace.class.getName();

    public interface Packet {
        public double ts();
        public long ts_millis();
        public byte[] payload();
        public int size();
    };
    
    protected final int mId;
    protected final short mDport;
    
    public Trace(int id, short dport) {
        mId = id;
        mDport = dport;
    }
    
    public int id() {
        return mId;
    }
    
    public short dport() {
        return mDport;
    }
    
    public abstract int version();
    
    @SuppressWarnings("rawtypes")
    public abstract ArrayList packets();
    
    public abstract int size();
    
    public abstract long durationMillis();
    
    public static Trace loadFromFile(String filename)
            throws IOException {
        
        Logger.i(sTag, "Loading trace file " + filename);
        
        DataInputStream input = null;
        try {
            /* start with reading the trace version, then select the appropriate read method */
            input = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(filename)));
            input.mark(4);
            int version = input.readInt();
            input.reset();
            switch (version) {
                case TraceV1.VERSION:
                    return TraceV1.loadFromStream(input);
                case RTPTrace.VERSION:
                    return RTPTrace.loadFromStream(input);
                default:
                    throw new IOException("Unknown trace version: " + version);
            }
        } catch (IOException e) {
            Logger.e(sTag, "Failed to read trace from file " + filename + ": " + e.getMessage());
            throw e;
        } finally {
            try { input.close(); } catch (Exception e) { }
        }
    }
}
