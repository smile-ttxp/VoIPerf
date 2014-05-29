package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.PacketFooter;
import it.uniroma1.voiperf.traces.Trace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class RandomTraceReceiver extends TraceReceiver {
    
    public static final String TYPENAME = "RANDOM_RECV";
    
    private static final String sTag = RandomTraceReceiver.class.getName();

    private ArrayList<Long> mRecvTimestamps;
    private ArrayList<Integer> mRecvSequenceNumbers;
    
    @SuppressWarnings({ "unchecked" })
    public static TraceStatistics receiveTrace(DataOutputStream ctrlOut, DataInputStream ctrlIn,
                                               String traceFilename, String firstHop)
        throws MeasurementException {
        
        try {
            // Load the trace and send its id to the server
            Trace trace = Trace.loadFromFile(traceFilename);

            Logger.i(sTag, "sending trace info");
            TraceReceiver.sendTraceInfo(ctrlOut, traceFilename, trace);
            
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
                pingFirstHop.setInterval(1.0/ Config.FIRST_HOP_PING_PER_SECOND);
                pingFirstHop.setPacketSize(Config.FIRST_HOP_PING_PACKET_SIZE);
                pingFirstHop.setDeadline((int) (trace.durationMillis() / 1000) +
                              Config.FIRST_HOPE_PING_MIN_DURATION_SECONDS);
                pingFirstHopThread = new Thread(pingFirstHop);
                pingFirstHopThread.start();
            }
            
            // Send the trace packets. This is blocking.
            RandomTraceReceiver receiver = new RandomTraceReceiver(trace, udpAddress, udpPort);
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
    
    public RandomTraceReceiver(Trace trace, String address, int port) {
        super(trace, address, port);
    }
    
    public ArrayList<Long> getRecvTimestamps() {
        return mRecvTimestamps;
    }
    
    public ArrayList<Integer> getRecvSequenceNumbers() {
        return mRecvSequenceNumbers;
    }
    
    @Override
    protected void sendRepliesInit() {
        mRecvTimestamps = new ArrayList<Long>(super.getTrace().size());
        mRecvSequenceNumbers = new ArrayList<Integer>(super.getTrace().size()); 
    }

    @Override
    protected boolean packetReceived(DatagramPacket receivedPacket,
                                     long now, DatagramPacket replyPacket) {
        mRecvTimestamps.add(Long.valueOf(now));
        int seq;
        try {
            seq = PacketFooter.getSequenceNumber(receivedPacket.getData(),
                                                 receivedPacket.getLength());
        } catch (IllegalArgumentException e) {
            // This is not an error, it's just that the trace packet was too small
            // to store a footer. We don't need to send back a reply to the server.
            mRecvSequenceNumbers.add(Integer.valueOf(-1));
            return false;
        }
        mRecvSequenceNumbers.add(Integer.valueOf(seq));
        System.arraycopy(receivedPacket.getData(), receivedPacket.getLength() - PacketFooter.SIZE,
                         replyPacket.getData(), 0, PacketFooter.SIZE);
        replyPacket.setLength(PacketFooter.SIZE);
        return true;
    }
}
