package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.RTPHeader;
import it.uniroma1.voiperf.traces.RTPTrace;
import it.uniroma1.voiperf.traces.Trace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class RTPTraceSender extends TraceSender {
    
    public static final String TYPENAME = "RTP_SEND";
    
    private static final String sTag = RTPTraceSender.class.getName();

    private final RTPTrace mTrace;
    
    private long[] mSentTimestamps;
    private RTPHeader[] mReplies;
    private long[] mRepliesTimestamps;
    
    @SuppressWarnings({ "unchecked" })
    public static TraceStatistics sendTrace(DataOutputStream ctrlOut, DataInputStream ctrlIn,
                                            String traceFilename, String firstHop)
        throws MeasurementException {
        
        try {
            // Load the trace and send its id to the server
            RTPTrace trace = RTPTrace.loadFromFile(traceFilename);
            
            Logger.i(sTag, "sending trace info");
            TraceSender.sendTraceInfo(ctrlOut, trace);
            
            // Read the address and port we have to connect to
            String udpAddress = ctrlIn.readLine();
            int udpPort = Integer.parseInt(ctrlIn.readLine());
            Logger.i(sTag, "trace will be sent to " + udpAddress + ":" + udpPort);
            
            // Start a concurrent ping to the first hop
            Thread pingFirstHopThread = null;
            Ping pingFirstHop = null;
            if (firstHop != null) {
                Logger.i(sTag, "starting first hop ping to " + firstHop);
                pingFirstHop = new Ping(firstHop);
                pingFirstHop.setInterval(1.0 / Config.FIRST_HOP_PING_PER_SECOND);
                pingFirstHop.setPacketSize(Config.FIRST_HOP_PING_PACKET_SIZE);
                pingFirstHop.setDeadline((int) (trace.durationMillis() / 1000) +
                              Config.FIRST_HOPE_PING_MIN_DURATION_SECONDS);
                pingFirstHopThread = new Thread(pingFirstHop);
                pingFirstHopThread.start();
            }
            
            // Send the trace packets. This is blocking.
            RTPTraceSender sender = new RTPTraceSender(trace, udpAddress, udpPort);
            sender.run();
            
            // Trace sent, wait for the ping command to finish
            if (pingFirstHopThread != null) {
                pingFirstHopThread.join();
            }
            
            // Send client info about the measurement
            JSONObject measurementInfo = new JSONObject();
            JSONObject localAddress = TraceSender.getLocalAddress(sender.getLocalAddressUsed(),
                                                                  sender.getLocalPortUsed());
            measurementInfo.put(Measurements.LOCAL_ADDRESS_INFO, localAddress);
            JSONArray traceRTTs = Utils.toJSONArray(sender.getRTTs());
            measurementInfo.put(Measurements.TRACE_RTTS_INFO, traceRTTs);
            JSONArray sentTimestamps = Utils.toJSONArray(sender.getSentTimestamps());
            measurementInfo.put(Measurements.SENT_TIMESTAMPS_INFO, sentTimestamps);
            if (pingFirstHop != null) {
                measurementInfo.put(Measurements.PING_RTTS_INFO, pingFirstHop.getResult());
            } else {
                measurementInfo.put(Measurements.PING_RTTS_INFO, null);
            }
            Utils.sendCompressedJSON(ctrlOut, measurementInfo);
            
            // Receive some statistics about the trace we just sent (e.g., packet loss, rate etc)
            return TraceSender.receiveTraceStatistics(ctrlIn);
        } catch (Exception e) {
            Logger.e(sTag, "Measurement failed: " + e.getMessage(), e);
            throw new MeasurementException("RTPTrace measurement failed");
        }
    }
    
    public RTPTraceSender(RTPTrace trace, String address, int port) {
        super(trace, address, port);
        mTrace = trace;
    }
    
    public long[] getRTTs() {
        long[] RTTs = new long[mRepliesTimestamps.length];
        for (int seq = 0; seq < mReplies.length; ++seq) {
            if (mReplies[seq] != null) {
                RTTs[seq] = mRepliesTimestamps[seq] - mSentTimestamps[seq];
            } else {
                RTTs[seq] = -1;
            }
        }
        return RTTs;
    }
    
    public long[] getSentTimestamps() {
        return mSentTimestamps;
    }
    
    public int[] getSentSequenceNumbers() {
        int[] seqs = new int[mSentTimestamps.length];
        for (int i = 0; i < seqs.length; ++i) {
            seqs[i] = i + mTrace.minSequenceNumber();
        }
        return seqs;
    }
    
    @Override
    protected void receiveRepliesInit() {
        mReplies = new RTPHeader[mTrace.sequenceNumberRange()];
        mRepliesTimestamps = new long[mTrace.sequenceNumberRange()];
    }
    
    @Override
    protected void replyReceived(DatagramPacket packet, long now) {
        RTPHeader header;
        try {
            header = RTPHeader.load(packet.getData(), packet.getLength());
        } catch (IllegalArgumentException e) {
            Logger.w(sTag, "received packet with no valid RTP header: " + e.getMessage());
            return;
        }

        int seq = header.sequenceNumber() - mTrace.minSequenceNumber();
        if (seq >= mReplies.length) {
            Logger.w(sTag,
                     "BUG: received packet with invalid sequence number (got" +
                     header.sequenceNumber() + ")");
            return;
        }
        if (mReplies[seq] == null) {
            mReplies[seq] = header;
            mRepliesTimestamps[seq] = now;
        }
    }
    
    @Override
    protected void sendPacketsInit() {
        mSentTimestamps = new long[mTrace.sequenceNumberRange()];
    }
    
    @Override
    protected boolean preparePacket(DatagramPacket rawPacket, Trace.Packet tracePacket) {
        
        RTPTrace.Packet rtpPacket = (RTPTrace.Packet) tracePacket;
        
        int seq = rtpPacket.getHeader().sequenceNumber() - mTrace.minSequenceNumber();
        if (seq >= mSentTimestamps.length) {
            Logger.w(sTag,
                     "BUG: tried to send packet with invalid sequence number ("+ seq +")");
            return false;
        }
        
        mSentTimestamps[seq] = System.currentTimeMillis();
        rawPacket.setData(rtpPacket.payload(), 0, rtpPacket.size());
        
        return true;
    }
}
