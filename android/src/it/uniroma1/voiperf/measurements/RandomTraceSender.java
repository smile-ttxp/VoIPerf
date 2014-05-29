package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.PacketFooter;
import it.uniroma1.voiperf.traces.Trace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class RandomTraceSender extends TraceSender {
    
    public static final String TYPENAME = "RANDOM_SEND";
    
    private static final String sTag = RandomTraceSender.class.getName();

    private PacketFooter[] mReplies;
    private long[] mRepliesTimestamps;
    private ByteBuffer mPayload;
    private int mSeq;
    
    @SuppressWarnings({ "unchecked" })
    public static TraceStatistics sendTrace(DataOutputStream ctrlOut, DataInputStream ctrlIn,
                                            String traceFilename, String firstHop)
       throws MeasurementException {
        
        try {
            // Load the trace and send its id to the server
            Trace trace = Trace.loadFromFile(traceFilename);
            
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
                pingFirstHop.setInterval(1.0/ Config.FIRST_HOP_PING_PER_SECOND);
                pingFirstHop.setPacketSize(Config.FIRST_HOP_PING_PACKET_SIZE);
                pingFirstHop.setDeadline((int) (trace.durationMillis() / 1000) +
                              Config.FIRST_HOPE_PING_MIN_DURATION_SECONDS);
                pingFirstHopThread = new Thread(pingFirstHop);
                pingFirstHopThread.start();
            }

            // Send the trace packets. This is blocking.
            RandomTraceSender sender = new RandomTraceSender(trace, udpAddress, udpPort);
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

    public RandomTraceSender(Trace trace, String address, int port) {
        super(trace, address, port);
    }
    
    public long[] getRTTs() {
        long[] RTTs = new long[mReplies.length];
        for (int seq = 0; seq < mReplies.length; ++seq) {
            if (mReplies[seq] != null) {
                RTTs[seq] = mRepliesTimestamps[seq] - mReplies[seq].timestamp();
            } else {
                RTTs[seq] = -1;
            }
        }
        return RTTs;
    }
    
    @Override
    protected void receiveRepliesInit() {
        mReplies = new PacketFooter[super.getTrace().size()];
        mRepliesTimestamps = new long[super.getTrace().size()];
    }
    
    @Override
    protected void replyReceived(DatagramPacket packet, long now) {
        PacketFooter footer = PacketFooter.load(packet.getData(), packet.getLength());
        if (footer == null) {
            Logger.w(sTag, "BUG: received packet with no footer?");
            return;
        } else if (footer.seq() >= mReplies.length) {
            Logger.w(sTag,
                     "BUG: received packet with invalid sequence number (got" +
                     footer.seq() + ")");
            return;
        }
        if (mReplies[footer.seq()] == null) {
            mReplies[footer.seq()] = footer;
            mRepliesTimestamps[footer.seq()] = now;
        }
    }
    
    @Override
    protected void sendPacketsInit() {
        int maxSize = Config.MAX_TRACE_PACKET_SIZE;
        mPayload = ByteBuffer.wrap(new byte[maxSize]).order(ByteOrder.BIG_ENDIAN);
        mSeq = -1;
    }
    
    @Override
    protected boolean preparePacket(DatagramPacket rawPacket, Trace.Packet tracePacket) {
        mSeq += 1;
        
        mPayload.rewind();
        if (mPayload.remaining() < tracePacket.size()) {
            Logger.w(sTag, "BUG: packet " + mSeq + " of trace is too big for our buffer");
            return false;
        }
        PacketFooter.append(tracePacket.payload(), mSeq, System.currentTimeMillis(), mPayload);
        rawPacket.setData(mPayload.array(), 0, tracePacket.size());
        return true;
    }
}
