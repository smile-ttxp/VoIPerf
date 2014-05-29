package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.logging.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Measurements {
    
    public static final String LOCAL_ADDRESS_INFO = "local_address";
    public static final String TRACE_RTTS_INFO = "trace_rtts";
    public static final String PING_RTTS_INFO = "ping_rtts";
    public static final String SENT_TIMESTAMPS_INFO = "sent_timestamps";
    public static final String RECV_TIMESTAMPS_INFO = "recv_timestamps";
    public static final String RECV_SEQ_INFO = "recv_seq";
    public static final int CONNECT_TIMEOUT = 60000;
    
    private static final String sTag = Measurements.class.getName();

    public static Socket connectToMeasurementServer() throws MeasurementException {
        try {
            InetSocketAddress address = new InetSocketAddress(Config.MEASUREMENT_SERVER_ADDRESS,
                    Config.MEASUREMENT_SERVER_PORT);
            Logger.i(sTag, "Connecting to measurement server " + address);
            Socket s = new Socket();
            s.connect(address, CONNECT_TIMEOUT);
            return s;
        } catch (IOException e) {
            throw new MeasurementException("Failed to connect to the measurement service", e);
        }
    }
    
    public static void closeMeasurementServerConnection(Socket server) {
        try {
            if (server != null) {
                server.close();
                Logger.i(sTag, "Disconnected from measurement server");
            }
        } catch (Exception e) {
            Logger.w(sTag, "Failed to close connection to measurement server");
        }
    }
}
