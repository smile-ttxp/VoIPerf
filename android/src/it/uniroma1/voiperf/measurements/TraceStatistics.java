package it.uniroma1.voiperf.measurements;

import org.json.simple.JSONObject;

public class TraceStatistics {

    public static final String PACKET_LOSS = "packet_loss";
    public static final String AVG_RATE = "avg_rate_kbits";
    
    private double mPacketLoss = 0.0;
    private double mRate = 0.0;
    private double mMOS = 0.0;
    private double mAverageJitter = 0.0;
    
    public TraceStatistics(JSONObject o) throws NullPointerException {
        mPacketLoss = (Double) o.get(PACKET_LOSS);       
        mRate = (Double) o.get(AVG_RATE);
    }
    
    public double getPacketLoss() {
        return (long) (mPacketLoss * 100);
    }
    
    public double getRate() {
        return (long) mRate;
    }
    
    public double getAverageJitter() {
        // TODO(claudiu) Include average jitter.
        return mAverageJitter;
    }
    
    public double getMOS() {
        // TODO(claudiu) Include MOS.
        return mMOS;
    }
}
