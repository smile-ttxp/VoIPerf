package it.uniroma1.voiperf.results;

public class Result {
    private long mTimestamp = 0;
    private String mType = "";
    private double mRate = 0.0;
    private double mPacketLoss = 1.2;
    private double mAverageJitter = 0.0;
    private double mMOS = 0.0;
    
    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }
    
    public long getTimestamp() {
        return mTimestamp;
    }
    
    public void setType(String type) {
        mType = type;
    }
    
    public String getType() {
        return mType;
    }
    
    public void setRate(double rate) {
        mRate = rate;
    }
    
    public double getRate() {
        return mRate;
    }
    
    public void setPacketLoss(double packetLoss) {
        mPacketLoss = packetLoss;
    }
    
    public double getPacketLoss() {
        return mPacketLoss;
    }
    
    public void setAverageJitter(double averageJitter) {
        mAverageJitter = averageJitter;
    }
    
    public double getAverageJitter() {
        return mAverageJitter;
    }
    
    public void setMOS(double MOS) {
        mMOS = MOS;
    }
    
    public double getMOS() {
        return mMOS;
    }
    
}
