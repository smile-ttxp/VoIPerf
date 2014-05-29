package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.Trace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public abstract class TraceSender {
    
    private static final String sTag = TraceSender.class.getName();

    private final Trace mTrace;
    private final SocketAddress mDest;
    
    private DatagramSocket mSocket;
    private String mLocalAddress;
    private int mLocalPort;
    
    private class SenderThread extends Thread {
        
        private MeasurementException mError = null;
        
        public boolean hasError() {
            return mError != null;
        }
        
        public MeasurementException getError() {
            return mError;
        }
        
        @Override
        public void run() {
            try {
                sendTracePackets();
            } catch (MeasurementException e) {
                Logger.e(sTag, "Failed to send trace: " + e.getMessage(), e);
                mError = e;
            }
        }
    }
    
    private class ReceiverThread extends Thread {
        
        private MeasurementException mError = null;
        
        public boolean hasError() {
            return mError != null;
        }
        
        public MeasurementException getError() {
            return mError;
        }
        
        @Override
        public void run() {
            try {
                receiveReplies();
            } catch (MeasurementException e) {
                Logger.e(sTag, "Failed to read trace replies: " + e.getMessage(), e);
                mError = e;
            }
        }
    }
    
    protected static void sendTraceInfo(DataOutputStream dos, Trace trace) throws IOException {
        dos.writeBytes(trace.id() + "\n");
        dos.writeBytes(trace.size() + "\n");
    }
    
    @SuppressWarnings("unchecked")
    protected static JSONObject getLocalAddress(String IP, int port) {
        JSONObject o = new JSONObject();
        o.put("IP", IP);
        o.put("port", port);
        return o;
    }
    
    protected static TraceStatistics receiveTraceStatistics(DataInputStream dis)
        throws IOException {
        byte[] data;
        try {
            int length = Integer.parseInt(dis.readLine());
            data = new byte[length];
            dis.readFully(data);
        } catch (NumberFormatException e) {
            Logger.e(sTag, "failed to read trace statistics: invalid length");
            throw new IOException("Failed to read trace statistics: invalid length");
        }
        
        try {
            return new TraceStatistics((JSONObject) JSONValue.parseWithException(new String(data)));
        } catch (ParseException e) {
            Logger.e(sTag, "failed to parse trace statistic's json: " + e.getMessage());
            throw new IOException("Failed to parse trace statistic's json: " + e.getMessage());
        } catch (NullPointerException e) {
            Logger.e(sTag, "failed to parse trace statistic's json: " + e.getMessage());
            throw new IOException("Failed to parse trace statistic's json " + e.getMessage());
        }
    }
    
    public TraceSender(Trace trace, String address, int port) {
        mTrace = trace;
        mDest = new InetSocketAddress(address, port);
    }
    
    public void run() throws MeasurementException {
        try {
            int socketReadTimeout = Config.TRACE_SOCKET_READ_TIMEOUT_MILLIS;
            int senderThreadWaitMillis = Config.SENDER_THREAD_WAIT_MILLIS;
            
            mSocket = new DatagramSocket();
            /* This timeout is needed to stop the receiver thread at the end of the
             * measurement. Since we are using UDP, we cannot be sure all the packets
             * will be received, so the timeout must be big enough (e.g., 3 seconds)
             * to let any incoming packet to be received. Note that calling interrupt()
             * on a thread waiting on a socket IO may not wake it, so the timeout is
             * NECESSARY */
            mSocket.setSoTimeout(socketReadTimeout);
            Logger.d(sTag, "socket read timeout set to " + socketReadTimeout + "ms");
            mSocket.setReuseAddress(true);
            mSocket.connect(mDest);
            mLocalAddress = mSocket.getLocalAddress().toString();
            mLocalPort = mSocket.getLocalPort();

            SenderThread sender = new SenderThread();
            ReceiverThread receiver = new ReceiverThread();
            sender.start();
            receiver.start();
            // give (more than) enough time for the thread to send all the data
            sender.join(mTrace.durationMillis() + senderThreadWaitMillis);
            // the receiver thread should have a timeout set, so just wait for it
            receiver.join();
            if (sender.hasError()) {
                throw new MeasurementException("Trace measurement failed", sender.getError());
            } else if (receiver.hasError()) {
                throw new MeasurementException("Trace measurement failed", receiver.getError());
            }
        } catch (IOException e) {
            Logger.e(sTag, "failed to send trace: " + e.getMessage());
            throw new MeasurementException("failed to send trace");
        } catch (InterruptedException e) {
            Logger.e(sTag, "send trace interrupted: " + e.getMessage());
            throw new MeasurementException("send trace interrupted");
        } finally {
            try { if (mSocket != null) mSocket.close(); } catch (Exception e) { }
            mSocket = null;
        }
    }
    
    public Trace getTrace() {
        return mTrace;
    }
    
    public String getLocalAddressUsed() {
        return mLocalAddress;
    }
    
    public int getLocalPortUsed() {
        return mLocalPort;
    }
    
    protected void receiveRepliesInit() { }
    
    protected abstract void replyReceived(DatagramPacket packet, long now);
    
    private void receiveReplies() throws MeasurementException {
        
        receiveRepliesInit();
        try {
            // TODO: we may very well ask the trace what is the maximum
            // packet size instead of using a predefined packet size
            int bsize = Config.MAX_TRACE_PACKET_SIZE;
            DatagramPacket packet = new DatagramPacket(new byte[bsize], bsize);
            while (true) {
                // this check is probably not necessary, but I want to
                // avoid getting stuck during a measurement (which would
                // basically make the app silently stop working).
                if (Thread.interrupted()) {
                    Logger.w(sTag, "interruped while sending trace");
                    throw new MeasurementException("Interrupted");
                }
                mSocket.receive(packet);
                replyReceived(packet, System.currentTimeMillis());
            }
        } catch (SocketTimeoutException e) {
            Logger.d(sTag, "socket timeout, assuming trace has finished");
        } catch (IOException e) {
            Logger.e(sTag, "error while reading trace replies: " + e);
            throw new MeasurementException(e.getMessage(), e);
        }
    }
    
    protected void sendPacketsInit() { }
    
    protected abstract boolean preparePacket(DatagramPacket rawPacket, Trace.Packet tracePacket);
    
    private void sendTracePackets() throws MeasurementException {
        sendPacketsInit();
        try {
            // TODO: we may very well ask the trace what is the maximum
            // packet size instead of using a predefined packet size
            String endMessage = Config.TRACE_END_MESSAGE;
            
            DatagramPacket rawPacket = new DatagramPacket(new byte[0], 0);
            long prevPacketTime = -1;
            for (Object p: mTrace.packets()) {
                
                Trace.Packet tracePacket = (Trace.Packet) p;
                
                // Compute the time that should pass between this and the previous packet
                long packetDelta = tracePacket.ts_millis() - prevPacketTime;
                if (prevPacketTime < 0) {
                    packetDelta = 0;
                }
                prevPacketTime = tracePacket.ts_millis();
                
                // Sleep
                if (packetDelta > 0) {
                    Utils.sleepMillisBusyWait(packetDelta);
                }
                
                // Prepare the packet and send it
                if (preparePacket(rawPacket, tracePacket)) {
                    mSocket.send(rawPacket);
                }
            }

            rawPacket.setData(endMessage.getBytes());
            mSocket.send(rawPacket);
            
        } catch (IOException e) {
            throw new MeasurementException(e.getMessage(), e);
        }
    }
}
