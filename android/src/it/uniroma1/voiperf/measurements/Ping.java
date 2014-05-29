package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.util.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Ping implements Runnable {
    
    public static final String PACKETS_TRANSMITTED = "packets_transmitted";
    public static final String PACKETS_RECEIVED = "packets_received";
    public static final String PACKET_LOSS = "packet_loss";
    public static final String TIME_MS = "time_ms";

    public static final String LINE = "line";
    
    public static final String RTT_MIN = "rtt_min";
    public static final String RTT_AVG = "rtt_avg";
    public static final String RTT_MAX = "rtt_max";
    public static final String RTT_MDEV = "rtt_mdev";;
    
    public static final String BYTES = "bytes";
    public static final String FROM = "from";
    public static final String ICMP_SEQ = "icmq_seq";
    public static final String TTL = "ttl";
    public static final String RTT = "rtt";
    public static final String REPLIES = "replies";
    public static final String PACKETS_STATS = "packets_stats";
    public static final String RTT_STATS = "rtt_stats";
    public static final String RET = "ret";
    public static final String STDERR = "stderr";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String INTERVAL = "interval";
    public static final String PACKETSIZE = "packet_size";
    public static final String COUNT = "count";
    public static final String DESTINATION = "destination";
    public static final String DEADLINE = "deadline";
    
    private static final String sTag = Ping.class.getName();
    
    private final String mDestination;
    
    private int mPacketSize = -1;
    private int mCount = -1;
    private int mDeadline = -1;
    private double mInterval = -1;
    
    private JSONObject mResult;
    private MeasurementException mError;
    
    public Ping(String destination) {
        mDestination = destination;
    }
    
    public void setDeadline(int deadline) {
        mDeadline = deadline;
    }
    
    public void setPacketSize(int size) {
        mPacketSize = size;
    }
    
    public void setCount(int count) {
        mCount = count;
    }
    
    public void setInterval(double interval) {
        mInterval = interval;
    }
    
    public JSONObject getResult() {
        return mResult;
    }
    
    public boolean hasError() {
        return mError != null;
    }
    
    public MeasurementException getError() {
        return mError;
    }
    
    private String generateCommandLine(String destination) {
        String command = "ping";
        if (mPacketSize > 0)
            command += " -s " + mPacketSize;
        if (mCount >= 0)
            command += " -c " + mCount;
        if (mDeadline > 0)
            command += " -w " + mDeadline;
        if (mInterval > 0)
            command += " -i " + mInterval;
        command += " " + destination;
        return command;
    }
    
    public void run() {
        
        mError = null;        
        String command = generateCommandLine(mDestination);
        try {
            Logger.i(sTag, "Running ping command: " + command);
            
            long startTime = System.currentTimeMillis();
            Process p = Runtime.getRuntime().exec(command);

            // TODO: warning, this way of reading output from a process may deadlock
            // implement a Popen.communicate()-like method in Utils and use that one instead
            BufferedReader obr = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuffer osb = new StringBuffer();
            String line;
            while ((line = obr.readLine()) != null) {
                osb.append(line).append("\n");
            }
            String output = osb.toString();

            BufferedReader ebr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuffer esb = new StringBuffer();
            while((line = ebr.readLine()) != null) {
                esb.append(line).append("\n");
            }
            String error = esb.toString();
            ebr.close();
            obr.close();

            int retVal = p.waitFor();
            long endTime = System.currentTimeMillis();
            Logger.i(sTag, "Ping finished");

            mResult = convertToJSON(output, error, retVal, mDestination, startTime, endTime);
        } catch (Exception e) {
            Logger.e(sTag, "Ping command error: " + e.getMessage());
            mError = new MeasurementException("Failed to run ping command: " + command, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private JSONObject convertToJSON(String output, String error, int retVal,
                                     String destination, long startTime, long endTime) {
        
        Logger.d(sTag, "convertToJSON");

        JSONObject ping = new JSONObject();
        String lines[] = output.split("\\n");
        
        // Parse the rtt of the replied pings
        JSONArray rttList = new JSONArray();
        Pattern reply_re = Pattern.compile("(\\S+) bytes from (\\S+\\.\\S+\\.\\S+\\.\\S+): " +
                                           "icmp_seq=(\\S+) ttl=(\\S+) time=(\\S+) ms");
        for (int i = 1; i < lines.length - 4; ++i) {
            JSONObject reply = new JSONObject();
            
            Matcher match = reply_re.matcher(lines[i]);
            if (match != null && match.find()) {
                reply.put(LINE, match.group(0));
                reply.put(BYTES, match.group(1));
                reply.put(FROM, match.group(2));
                reply.put(ICMP_SEQ, match.group(3));
                reply.put(TTL, match.group(4));
                reply.put(RTT, match.group(5));
            } else {
                Logger.w(sTag, "\"" + lines[i] + "\" Not Matched");
                reply.put(LINE, lines[i]);
                reply.put(BYTES, null);
                reply.put(FROM, null);
                reply.put(ICMP_SEQ, null);
                reply.put(TTL, null);
                reply.put(RTT, null);
            }
            rttList.add(reply);
        }
        ping.put(REPLIES, rttList);
        
        // Parse the statistics reported by ping
        JSONObject packets_stats = new JSONObject();
        packets_stats.put(LINE, null);
        packets_stats.put(RTT_MIN, null);
        packets_stats.put(RTT_AVG, null);
        packets_stats.put(RTT_MAX, null);
        packets_stats.put(RTT_MDEV, null);
        Pattern stats_re = Pattern.compile("(\\S+) packets transmitted, (\\S+) received, " +
                                           "(\\S+)% packet loss, time (\\S+)ms");
        Matcher match = null;
        if (lines.length >= 2) {
            packets_stats.put(LINE, lines[lines.length - 2]);
            match = stats_re.matcher(lines[lines.length - 2]);
            if (match != null && match.find()) {
                packets_stats.put(LINE, match.group(0));
                packets_stats.put(PACKETS_TRANSMITTED, match.group(1));
                packets_stats.put(PACKETS_RECEIVED, match.group(2));
                packets_stats.put(PACKET_LOSS, match.group(3));
                packets_stats.put(TIME_MS, match.group(4));
            } else {
                Logger.w(sTag, "\"" + lines[lines.length - 2] + "\" Not Matched");
            }
        }
        ping.put(PACKETS_STATS, packets_stats);
        
        JSONObject rtt_stats = new JSONObject();
        rtt_stats.put(LINE, null);
        rtt_stats.put(RTT_MIN, null);
        rtt_stats.put(RTT_AVG, null);
        rtt_stats.put(RTT_MAX, null);
        rtt_stats.put(RTT_MDEV, null);
        Pattern rtt_re = Pattern.compile("rtt min/avg/max/mdev = (\\S+)/(\\S+)/(\\S+)/(\\S+) ms");
        match = null;
        if (lines.length >= 1) {
            rtt_stats.put(LINE, lines[lines.length - 1]);
            match = rtt_re.matcher(lines[lines.length - 1]);
            if (match != null && match.find()) {
                rtt_stats.put(RTT_MIN, match.group(1));
                rtt_stats.put(RTT_AVG, match.group(2));
                rtt_stats.put(RTT_MAX, match.group(3));
                rtt_stats.put(RTT_MDEV, match.group(4));
            } else {
                Logger.w(sTag, "\"" + lines[lines.length - 2] + "\" Not Matched");
                rtt_stats.put(LINE, lines[lines.length - 1]);
                rtt_stats.put(RTT_MIN, null);
                rtt_stats.put(RTT_AVG, null);
                rtt_stats.put(RTT_MAX, null);
                rtt_stats.put(RTT_MDEV, null);
            }
        }
        ping.put(RTT_STATS, rtt_stats);
        
        ping.put(RET, retVal);
        ping.put(STDERR, error);
        ping.put(START_TIME, Utils.ms_to_s(startTime));
        ping.put(END_TIME, Utils.ms_to_s(endTime));
        ping.put(INTERVAL, mInterval);
        ping.put(PACKETSIZE, mPacketSize);
        ping.put(COUNT, mCount);
        ping.put(DEADLINE, mDeadline);
        ping.put(DESTINATION, destination);
        
        return ping;
    }
}
