package it.uniroma1.voiperf.traces;

public class RTPHeader {

    public static int SIZE = 12;

    private int mVersion;
    private int mPadding;
    private int mExtension;
    private int mCC;
    private int mMarker;
    private int mPayloadType;
    private int mSequenceNumber;
    private int mTimestamp;
    private int mSsrc;

    public static RTPHeader load(byte[] data, int length) throws IllegalArgumentException {
        RTPHeader h = new RTPHeader();
        h.mVersion = getVersion(data, length);
        h.mPadding = getPadding(data, length);
        h.mExtension = getExtension(data, length);
        h.mCC = getCC(data, length);

        h.mMarker = getMarker(data, length);
        h.mPayloadType = getPayloadType(data, length);

        h.mSequenceNumber = getSequenceNumber(data, length);
        h.mTimestamp = getTimestamp(data, length);
        h.mSsrc = getSsrc(data, length);
        return h;
    }
    
    public static int getVersion(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[0] >> 6) & 0x3;
    }
    
    public static int getPadding(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[0] >> 5) & 0x1;
    }
    
    public static int getExtension(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[0] >> 4) & 0x1;
    }
    
    public static int getCC(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return data[0] & 0x0F;
    }
    
    public static int getMarker(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[1] >> 7) & 0x1;
    }
    
    public static int getPayloadType(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return data[1] & 0x7F;
    }
    
    public static int getSequenceNumber(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[2] << 8) & 0xFF00 | data[3] & 0xFF;
    }
    
    public static int getTimestamp(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[4] << 24) & 0xFF000000 | (data[5] << 16) & 0xFF0000 |
               (data[6] <<  8) & 0xFF00 | data[7] & 0xFF;
    }
    
    public static void setTimestamp(byte[] data, int length, int timestamp)
        throws IllegalArgumentException {
        checkSize(length);
        data[4] = (byte) (timestamp >> 24);
        data[5] = (byte) (timestamp >> 16);
        data[6] = (byte) (timestamp >> 8);
        data[7] = (byte) timestamp;
    }
    
    public static int getSsrc(byte[] data, int length) throws IllegalArgumentException {
        checkSize(length);
        return (data[8] << 24) & 0xFF000000 | (data[9] << 16) & 0xFF0000 |
               (data[10] << 8) & 0xFF00 | data[11] & 0xFF;
    }
    
    private static void checkSize(int length) throws IllegalArgumentException {
        if (length < SIZE) {
            throw new IllegalArgumentException("RTP Header data buffer is too small");
        }
    }
    
    private RTPHeader() { }
    
    public int version() { return mVersion; }
    public int padding() { return mPadding; }
    public int extension() { return mExtension; }
    public int CC() { return mCC; }
    public int marker() { return mMarker; }
    public int payloadType() { return mPayloadType; }
    public int sequenceNumber() { return mSequenceNumber; }
    public int timestamp() { return mTimestamp; }
    public int ssrc() { return mSsrc; }
    
    public void setTimestamp(int timestamp) {
        mTimestamp = timestamp;
    }
    
    public void setSequenceNumber(int sequenceNumber) {
        mSequenceNumber = sequenceNumber;
    }
    
    public boolean writeTo(byte[] data) {
        if (data.length < SIZE) {
            return false;
        }
        data[0] = (byte) ((mVersion << 6) & 0xC0 | (mPadding << 5) & 20 |
                          (mExtension << 4) & 0x10 | mCC & 0xF);
        data[1] = (byte) ((mMarker << 7) & 0x80 | mPayloadType & 0x7F);
        data[2] = (byte) (mSequenceNumber >> 8);
        data[3] = (byte) mSequenceNumber;
        data[4] = (byte) (mTimestamp >> 24);
        data[5] = (byte) (mTimestamp >> 16);
        data[6] = (byte) (mTimestamp >> 8);
        data[7] = (byte) mTimestamp;
        data[8] = (byte) (mSsrc >> 24);
        data[9] = (byte) (mSsrc >> 16);
        data[10] = (byte) (mSsrc >> 8);
        data[11] = (byte) mSsrc;
        
        return true;
    }
}