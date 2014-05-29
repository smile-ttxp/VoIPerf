package it.uniroma1.voiperf.traces;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PacketFooter {
    
    public static final int SEQ_SIZE = Integer.SIZE / 8;
    public static final int TIMESTAMP_SIZE = Long.SIZE / 8;
    public static final int SIZE = SEQ_SIZE + TIMESTAMP_SIZE;

    private final int mSeq;
    private final long mTimestamp;
    
    public PacketFooter(int seq, long timestamp) {
        mSeq = seq;
        mTimestamp = timestamp;
    }
    
    public int seq() {
        return mSeq;
    }
    
    public long timestamp() {
        return mTimestamp;
    }
    
    public boolean append(byte[] payload, ByteBuffer output) {
        return PacketFooter.append(payload, mSeq, mTimestamp, output);
    }
    
    public static int getSequenceNumber(byte[] payload, int length)
        throws IllegalArgumentException {
        if (length < SIZE) {
            throw new IllegalArgumentException("Packet footer data buffer is too small");
        }
        int seq = (payload[length - SIZE] << 24) & 0xFF000000 |
                  (payload[length - SIZE + 1] << 16) & 0xFF0000 |
                  (payload[length - SIZE + 2] <<  8) & 0xFF00 |
                  payload[length - SIZE + 3] & 0xFF;
        return seq;
    }
    
    public static PacketFooter load(byte[] payload, int length) {
        if (length < SIZE) {
            // not enough bytes for the footer to be in the packet
            return null;
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(payload, 0, length).order(ByteOrder.BIG_ENDIAN);
            int seq = buffer.getInt();
            long timestamp = buffer.getLong();
            return new PacketFooter(seq, timestamp);
        }
    }
    
    public static boolean append(byte[] payload, int seq, long timestamp, ByteBuffer output)
        throws BufferOverflowException {
        
        // Set the packet footer and send it
        if (output.remaining() < payload.length) {
            output.put(payload, 0, output.remaining());
            return false;
        } else if (payload.length < SIZE) {
            // not enough space to insert the footer
            output.put(payload);
            return false;
        } else {
            output.put(payload, 0, payload.length - SIZE);
            output.putInt(seq);
            output.putLong(timestamp);
            return true;
        }
    }
}
