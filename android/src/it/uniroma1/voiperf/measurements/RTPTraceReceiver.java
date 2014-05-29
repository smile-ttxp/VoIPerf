package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.RTPHeader;
import it.uniroma1.voiperf.traces.RTPTrace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class RTPTraceReceiver extends TraceReceiver {
    
    public static final String TYPENAME = "RTP_RECV";
    
    private static final String sTag = RTPTraceReceiver.class.getName();

    private final RTPTrace mTrace;
    
    private ArrayList<Long> mRecvTimestamps;
    private ArrayList<Integer> mRecvSequenceNumbers;
    
    @SuppressWarnings({ "unchecked" })
    public static TraceStatistics receiveTrace(DataOutputStream ctrlOut, DataInputStream ctrlIn,
                                               String traceFilename, String firstHop)
        throws MeasurementException {
        
        try {
            // Load the trace and send its id to the server
            RTPTrace trace = RTPTrace.loadFromFile(traceFilename);
            
            Logger.i(sTag, "sending trace info");
            TraceReceiver.sendTraceInfo(ctrlOut, traceFilename, trace);
            
            // Read the address and port we have to connect to
            String udpAddress = ctrlIn.readLine();
            Logger.i(sTag, "udpAddress is " + udpAddress);
            int udpPort = Integer.parseInt(ctrlIn.readLine());
            Logger.i(sTag, "trace will be sent to " + udpAddress + ":" + udpPort);
            
            // Start a concurrent ping to the first hop
            Thread pingFirstHopThread = null;
            Ping pingFirstHop = null;
            if (firstHop != null) {
                Logger.i(sTag, "starting first hop ping to " + firstHop);
                pingFirstHop = new Ping(firstHop);
                pingFirstHop.setInterval(1.0/ Config.FIRST_HOP_PING_PER_SECOND);
                pingFirstHop.setPacketSize(Config.FIRST_HOP_PING_PACKET_SIZE);
                pingFirstHop.setDeadline((int) (trace.durationMillis() / 1000) +
                              Config.FIRST_HOPE_PING_MIN_DURATION_SECONDS);
                pingFirstHopThread = new Thread(pingFirstHop);
                pingFirstHopThread.start();
            }
            
            // Send the trace packets. This is blocking.
            RTPTraceReceiver receiver = new RTPTraceReceiver(trace, udpAddress, udpPort);
            receiver.run();
            
            // Trace sent, wait for the ping command to finish
            if (pingFirstHopThread != null) {
                pingFirstHopThread.join();
            }
            
            // Send client info about the measurement
            JSONObject measurementInfo = new JSONObject();
            JSONObject localAddress = TraceReceiver.getLocalAddress(receiver.getLocalAddressUsed(),
                                                                    receiver.getLocalPortUsed());
            measurementInfo.put(Measurements.LOCAL_ADDRESS_INFO, localAddress);
            JSONArray recvTimestamps = Utils.toJSONArray(receiver.getRecvTimestamps());
            measurementInfo.put(Measurements.RECV_TIMESTAMPS_INFO, recvTimestamps);
            JSONArray recvSequenceNumbers = Utils.toJSONArray(receiver.getRecvSequenceNumbers());
            measurementInfo.put(Measurements.RECV_SEQ_INFO, recvSequenceNumbers);
            if (pingFirstHop != null) {
                measurementInfo.put(Measurements.PING_RTTS_INFO, pingFirstHop.getResult());
            } else {
                measurementInfo.put(Measurements.PING_RTTS_INFO, null);
            }
            Utils.sendCompressedJSON(ctrlOut, measurementInfo);
            
            // Receive some statistics about the trace we just sent (e.g., packet loss, rate etc)
            return TraceReceiver.receiveTraceStatistics(ctrlIn);
        } catch (Exception e) {
            Logger.e(sTag, "Measurement failed: " + e.getMessage(), e);
            throw new MeasurementException("RTPTrace measurement failed");
        }
    }
    
    public RTPTraceReceiver(RTPTrace trace, String address, int port) {
        super(trace, address, port);
        mTrace = trace;
    }
    
    public ArrayList<Long> getRecvTimestamps() {
        return mRecvTimestamps;
    }
    
    public ArrayList<Integer> getRecvSequenceNumbers() {
        return mRecvSequenceNumbers;
    }
    
    @Override
    protected void sendRepliesInit() {
        // We expect to receive somewhere around mTrace.sequenceNumberRange() packets
        // (something less or a little more, depending on packet loss and/or packet duplication)
        mRecvTimestamps = new ArrayList<Long>(mTrace.sequenceNumberRange());
        mRecvSequenceNumbers = new ArrayList<Integer>(mTrace.sequenceNumberRange());  
    }
    
    @Override
    protected boolean packetReceived(DatagramPacket receivedPacket,
                                     long now, DatagramPacket replyPacket) {
        mRecvTimestamps.add(Long.valueOf(now));
        int seq;
        try {
            seq = RTPHeader.getSequenceNumber(receivedPacket.getData(), receivedPacket.getLength());
        } catch (IllegalArgumentException e) {
            // RTP traces should (be definition) always store an RTP header
            Logger.w(sTag, "received packet with no valid RTP header: " + e.getMessage());
            mRecvSequenceNumbers.add(Integer.valueOf(-1));
            return false;
        }
        mRecvSequenceNumbers.add(Integer.valueOf(seq));
        System.arraycopy(receivedPacket.getData(), 0, replyPacket.getData(), 0, RTPHeader.SIZE);
        replyPacket.setLength(RTPHeader.SIZE);
        return true;
    }
}
