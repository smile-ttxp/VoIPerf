package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.traces.Trace;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public abstract class TraceReceiver {
    
    private static final String sTag = TraceReceiver.class.getName();

    private final Trace mTrace;
    private final SocketAddress mDest;
    
    private DatagramSocket mSocket;
    private String mLocalAddress;
    private int mLocalPort;
    
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
                receiveTracePackets();
            } catch (MeasurementException e) {
                Logger.e(sTag, "Failed to receive trace packets: " + e.getMessage(), e);
                mError = e;
            }
        }
    }
    
    protected static void sendTraceInfo(DataOutputStream dos,
                                      String filename,
                                      Trace trace) throws IOException {
        dos.writeBytes(new File(filename).getName() + "\n");
        dos.writeBytes(trace.id() + "\n");
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

    public TraceReceiver(Trace trace, String address, int port) {
        mTrace = trace;
        mDest = new InetSocketAddress(address, port);
    }
    
    public void run() throws MeasurementException {
        try {
            int senderThreadWaitMillis = Config.SENDER_THREAD_WAIT_MILLIS;
            
            mSocket = new DatagramSocket();
            mSocket.setReuseAddress(true);
            mSocket.connect(mDest);
            mLocalAddress = mSocket.getLocalAddress().toString();
            mLocalPort = mSocket.getLocalPort();

            ReceiverThread receiver = new ReceiverThread();
            receiver.start();
            
            // give (more than) enough time for the thread to receive all the data
            receiver.join(mTrace.durationMillis() + senderThreadWaitMillis);
            if (receiver.hasError()) {
                throw new MeasurementException("Trace measurement failed", receiver.getError());
            }
            
        } catch (IOException e) {
            Logger.e(sTag, "failed to receive trace: " + e.getMessage());
            throw new MeasurementException("failed to receive trace");
        } catch (InterruptedException e) {
            Logger.e(sTag, "receive trace interrupted: " + e.getMessage());
            throw new MeasurementException("receive trace interrupted");
        } finally {
            try { if (mSocket != null) mSocket.close(); } catch (Exception e) { }
            mSocket = null;
        }
    }
    
    public String getLocalAddressUsed() {
        return mLocalAddress;
    }
    
    public int getLocalPortUsed() {
        return mLocalPort;
    }
    
    public Trace getTrace() {
        return mTrace;
    }
    
    protected void sendRepliesInit() { }
    
    protected abstract boolean packetReceived(DatagramPacket receivedPacket,
                                              long now,
                                              DatagramPacket replyPacket);
    
    private void receiveTracePackets() throws MeasurementException {
        
        // Set a large send buffer size so as to minimize the
        // probability of getting stuck when sending the replies
        // to the client
        try {
            int bsize = Config.TRACE_SOCKET_SEND_BUFFER_SIZE;
            mSocket.setSendBufferSize(bsize);
            Logger.d(sTag, "trace connection send buffer size set to " +
                     mSocket.getSendBufferSize() + "bytes");
            bsize = Config.TRACE_SOCKET_RECV_BUFFER_SIZE;
            mSocket.setReceiveBufferSize(bsize);
            Logger.d(sTag, "trace connection recv buffer size set to " +
                     mSocket.getReceiveBufferSize() + "bytes");
        } catch (SocketException e) {
            Logger.w(sTag, "failed to set trace connection buffer size: " + e.getMessage());
        }
        
        int socketWaitTimeout = Config.TRACE_SOCKET_WAIT_TIMEOUT_MILLIS;
        int socketReadTimeout = Config.TRACE_SOCKET_READ_TIMEOUT_MILLIS;
        byte[] endMsg = Config.TRACE_END_MESSAGE.getBytes();

        boolean firstPacket = true;
        try {
            int packetSize = Config.MAX_TRACE_PACKET_SIZE;
            DatagramPacket receivedPacket = new DatagramPacket(new byte[packetSize],packetSize);
            DatagramPacket replyPacket = new DatagramPacket(new byte[packetSize], packetSize);
            
            /* This timeout is needed to stop this thread at the end of the
             * measurement. Since we are using UDP, we cannot be sure all the packets
             * will be received, so the timeout must be big enough (e.g., 3 seconds)
             * to let any incoming packet to be received. Note that calling interrupt()
             * on a thread waiting on a socket IO may not wake it, so the timeout is
             * NECESSARY */
            mSocket.setSoTimeout(socketWaitTimeout);
            Logger.d(sTag, "trace connection wait timeout set to " + socketWaitTimeout + "ms");

            // Start by sending a NAT hole punching packet,
            // so the server can send us back the trace packets
            holePunching(Config.HOLE_PUNCHING_MESSAGE.getBytes(),
                         Config.HOLE_PUNCHING_RETRIES,
                         Config.HOLE_PUNCHING_WAIT_TIME_MILLIS);

            sendRepliesInit();
            
            // Receive the trace packets in sequence
            while (true) {
                // this check is probably not necessary, but I want to
                // avoid getting stuck during a measurement (which would
                // basically make the app silently stop working).
                if (Thread.interrupted()) {
                    Logger.w(sTag, "interruped while sending trace");
                    throw new MeasurementException("Interrupted");
                }

                // Receive the next packet
                mSocket.receive(receivedPacket);
                if (firstPacket) {
                    Logger.d(sTag, "first packet received");
                    // the first packet has been received.
                    // Reduce the socket timeout.
                    firstPacket = false;
                    mSocket.setSoTimeout(socketReadTimeout);
                    Logger.d(sTag, "trace connection receive timeout set to "
                             + socketReadTimeout + "ms");
                }
                long now = System.currentTimeMillis();
                if (isEndMsg(receivedPacket.getData(), receivedPacket.getLength(), endMsg)) {
                    // The trace has finished
                    Logger.i(sTag, "end trace received");
                    break;
                }
                
                if (packetReceived(receivedPacket, now, replyPacket)) {
                    mSocket.send(replyPacket);
                }
            }
        } catch (SocketTimeoutException e) {
            Logger.d(sTag, "socket timeout, assuming trace has finished");
        } catch (IOException e) {
            Logger.e(sTag, "error while reading trace replies: " + e);
            throw new MeasurementException(e.getMessage(), e);
        }
    }
    
    private void holePunching(byte[] punchMsg, int nPackets, long waitTime) throws IOException {
        DatagramPacket dp = new DatagramPacket(punchMsg, punchMsg.length);
        for (int i = 0; i < nPackets; ++i) {
            mSocket.send(dp);
            try { Utils.sleepMillis(waitTime); } catch (InterruptedException e) { }
        }
        Logger.d(sTag, "hole punching packets sent");
    }
    
    private boolean isEndMsg(byte[] data, int dataLength, byte[] endMsg) {
        if (dataLength != endMsg.length) {
            return false;
        }
        for (int i = 0; i < endMsg.length; ++i) {
            if (data[i] != endMsg[i]) {
                return false;
            }
        }
        return true;
    }
}
