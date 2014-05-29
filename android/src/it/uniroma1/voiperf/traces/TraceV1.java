package it.uniroma1.voiperf.traces;

import it.uniroma1.voiperf.logging.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TraceV1 extends Trace {

    public static final int VERSION = 1;
    
    private static final String sTag = TraceV1.class.getName();
    
    public static class Packet implements Trace.Packet {
        private final long mTs_millis;
        private final double mTs;
        private final byte[] mPayload;

        public Packet(double ts, byte[] payload) {
            mTs = ts;
            mTs_millis = (long) (ts * 1000);
            mPayload = payload;
        }
        
        public double ts() {
            return mTs;
        }
        
        public long ts_millis() {
            return mTs_millis;
        }
        
        public byte[] payload() {
            return mPayload;
        }
        
        public int size() {
            return mPayload.length;
        }
    };
    
    protected final ArrayList<Packet> mPackets;
    
    public TraceV1(int id, short dport, ArrayList<Packet> packets) {
        super(id, dport);
        if (packets == null) {
            mPackets = new ArrayList<Packet>();
        } else {
            mPackets = packets;
        }
    }
    
    public TraceV1(int id, short dport) {
        this(id, dport, null);
    }
    
    public void addPacket(Packet packet) {
        mPackets.add(packet);
    }
    
    public int version() {
        return VERSION;
    }
    
    @Override @SuppressWarnings("rawtypes")
    public ArrayList packets() {
        return mPackets;
    }
    
    @Override
    public int size() {
        return mPackets.size();
    }
    
    @Override
    public long durationMillis() {
        if (mPackets.isEmpty())
            return 0;
        return mPackets.get(mPackets.size() - 1).ts_millis() - mPackets.get(0).ts_millis();
    }
    
    public static TraceV1 loadFromFile(String filename) throws IOException {
                      
        Logger.i(sTag, "Loading trace file " + filename);

        DataInputStream input = null;
        try {
            /* start with reading the trace version, then select the appropriate read method */
            input = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
            return TraceV1.loadFromStream(input);
        } catch (IOException e) {
            Logger.e(sTag, "Failed to read trace from file " + filename + ": " + e.getMessage());
            throw e;
        } finally {
            try { input.close(); } catch (Exception e) { }
        }
    }

    public static TraceV1 loadFromStream(DataInputStream input) throws IOException {
        try {
            /* read the preamble */
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("Invalid trace version (got " + version + ")");
            }
            int id = input.readInt();
            short dport = input.readShort();
            
            TraceV1 trace = new TraceV1(id, dport);
            
            /* read each packet in turn */
            while (true) {
                Packet packet = readPacket(input);
                if (packet != null) {
                    trace.addPacket(packet);
                } else {
                    break;
                }
            }
            
            return trace;
        } catch (EOFException e) {
            throw new IOException("Unexpected end of file");
        }
    }
    
    private static Packet readPacket(DataInputStream input)
            throws IOException {
        try {
            double ts = input.readDouble();
            short length = input.readShort();
            byte[] payload = new byte[length];
            input.read(payload);
            //Logger.d(sTag, "read packet with length " + length + " and payload " + Utils.bytesToHex(payload));
            return new Packet(ts, payload);
        } catch (EOFException e) {
            return null;
        }
    }
}
