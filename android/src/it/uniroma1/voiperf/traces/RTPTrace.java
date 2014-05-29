package it.uniroma1.voiperf.traces;

import it.uniroma1.voiperf.logging.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

public class RTPTrace extends Trace {

    public static final int VERSION = 3;
    
    private static final String sTag = RTPTrace.class.getName();
    
    public static class Packet implements Trace.Packet {
        private final long mTs_millis;
        private final double mTs;
        private final RTPHeader mRTPHeader;
        private final byte[] mPayload;

        public Packet(double ts, RTPHeader header, byte[] payload) {
            mTs = ts;
            mTs_millis = (long) (ts * 1000);
            mRTPHeader = header;
            mPayload = payload;
        }
        
        public double ts() {
            return mTs;
        }
        
        public long ts_millis() {
            return mTs_millis;
        }
        
        public RTPHeader getHeader() {
            return mRTPHeader;
        }
        
        public byte[] payload() {
            return mPayload;
        }
        
        public int size() {
            return mPayload.length;
        }
    };
    
    protected final ArrayList<Packet> mPackets;
    private int mMinSequenceNumber = -1;
    private int mMaxSequenceNumber = -1;
    
    public RTPTrace(int id, short dport, ArrayList<Packet> packets) {
        super(id, dport);
        if (packets == null) {
            mPackets = new ArrayList<Packet>();
        } else {
            mPackets = packets;
        }
    }
    
    public RTPTrace(int id, short dport) {
        this(id, dport, null);
    }
    
    public void addPacket(Packet packet) {
        int seq = packet.getHeader().sequenceNumber();
        if (mMinSequenceNumber < 0 || seq < mMinSequenceNumber) {
            mMinSequenceNumber = seq;
        }
        if (mMaxSequenceNumber < seq) {
            mMaxSequenceNumber = seq;
        }
        mPackets.add(packet);
    }
    
    public Packet getPacket(int index) {
        return mPackets.get(index);
    }
    
    @Override
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
    
    public int minSequenceNumber() {
        return mMinSequenceNumber;
    }
    
    public int maxSequenceNumber() {
        return mMaxSequenceNumber;
    }
    
    public int sequenceNumberRange() {
        return mMaxSequenceNumber - mMinSequenceNumber + 1;
    }
    
    @Override
    public long durationMillis() {
        if (mPackets.isEmpty())
            return 0;
        return mPackets.get(mPackets.size() - 1).ts_millis() - mPackets.get(0).ts_millis();
    }
    
    public static RTPTrace loadFromFile(String filename) throws IOException {
                      
        Logger.i(sTag, "Loading trace file " + filename);

        DataInputStream input = null;
        try {
            /* start with reading the trace version, then select the appropriate read method */
            input = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
            return RTPTrace.loadFromStream(input);
        } catch (IOException e) {
            Logger.e(sTag, "Failed to read trace from file " + filename + ": " + e.getMessage());
            throw e;
        } finally {
            try { input.close(); } catch (Exception e) { }
        }
    }

    public static RTPTrace loadFromStream(DataInputStream input) throws IOException {
        HashSet<Integer> sequenceNumbers = new HashSet<Integer>();
        try {
            /* read the preamble */
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("Invalid trace version (got " + version + ")");
            }
            int id = input.readInt();
            short dport = input.readShort();
            
            RTPTrace trace = new RTPTrace(id, dport);
            
            /* read each packet in turn, ensure the RTP header is there */
            while (true) {
                Packet packet = readPacket(input);
                if (packet != null) {
                    Integer seq = Integer.valueOf(packet.getHeader().sequenceNumber());
                    if (sequenceNumbers.add(seq) == false) {
                        Logger.w(sTag, "duplicate RTP sequence number detected! Skipping it");
                        continue;
                    }
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
            RTPHeader h = RTPHeader.load(payload, length);
            if (h == null) {
                throw new IOException("Invalid RTP header");
            }
            return new Packet(ts, h, payload);
        } catch (EOFException e) {
            return null;
        }
    }    
}
