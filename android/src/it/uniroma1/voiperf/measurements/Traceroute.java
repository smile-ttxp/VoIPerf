package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.util.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.content.Context;

public class Traceroute implements Runnable {

    public static final String INFO = "info";
    public static final String LINE = "line";
    public static final String RTT = "rtt";
    public static final String HOST_NAME = "host_name";
    public static final String HOST = "host";
    public static final String MAX_HOPS = "max_hops";
    public static final String PACKETS_BYTES = "packets_bytes";
    public static final String UNKNOWN = "unknown";
    public static final String HOP_NUMBER = "hop_number";
    public static final String RET = "ret";
    public static final String STDERR = "stderr";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String HOPS = "hops";
    public static final String FIRST_HOP = "firsthop";
    
    private static final String sTag = Traceroute.class.getName();

    private final String mDestination;

    private int mMaxTTL = -1;
    
    private JSONObject mResult;
    private MeasurementException mError;
    
    private String generateCommandLine(String destination) {
        
        Context context = Session.getGlobalContext();
        String traceroutePath = context.getFileStreamPath("traceroute").getAbsolutePath();
        
        String command = traceroutePath;
        if (mMaxTTL > 0)
            command += " -m " + mMaxTTL;
        command += " " + destination;
        
        return command;
    }
    
    public Traceroute(String destination) {
        mDestination = destination;
    }
    
    public void setMaxTTL(int ttl) {
        mMaxTTL = ttl;
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
    
    public void run() {
        
        mError = null;
        String command = generateCommandLine(mDestination);
        try {
            Logger.i(sTag, "Running traceroute command: " + command);
            
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
            Logger.i(sTag, "Traceroute finished");
            
            mResult = convertToJSON(output, error, retVal, mDestination, startTime, endTime);
        } catch (Exception e) {
            Logger.e(sTag, "Traceroute command error: " + e.getMessage());
            mError = new MeasurementException("Failed to run traceroute command: " + command, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public String findFirstHop() throws MeasurementException {
        
        if (mResult == null) {
            return null;
        }
        for (Object hop: (JSONArray) mResult.get(Traceroute.HOPS)) {
            String host = (String) ((JSONObject) hop).get(Traceroute.HOST);
            Ping ping = new Ping(host);
            ping.setCount(Config.DISCOVERY_PING_COUNT);
            ping.run();
            if (isReachable(ping.getResult())) {
                mResult.put(Traceroute.FIRST_HOP, host);
                return host;
            }
        }
        return null;
    }
    
    private boolean isReachable(JSONObject pingResult) {
        try {
            String received = (String) ((JSONObject) pingResult
                                            .get(Ping.PACKETS_STATS)).get(Ping.PACKETS_RECEIVED);
            return Integer.parseInt(received) > 0;
        } catch (NullPointerException e) {
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    private JSONObject convertToJSON(String output, String error, int retVal,
                                     String destination, long startTime, long endTime) {
        
        Logger.d(sTag, "convertToJSON");
        
        JSONObject traceroute = new JSONObject();
        String lines[] = output.split("\\n");
        
        JSONObject info = new JSONObject();
        info.put(LINE, null);
        info.put(HOST_NAME, null);
        info.put(HOST, null);
        info.put(MAX_HOPS, null);
        info.put(PACKETS_BYTES, null);
        Pattern header = Pattern.compile("traceroute to (\\S+) \\((\\S+)\\), " +
        		"(\\d+) hops max, (\\d+) byte packets");
        if (lines.length > 0) {
            info.put(LINE, lines[0]);
            Matcher match = header.matcher(lines[0]);
            if (match != null && match.find()) {
                info.put(HOST_NAME, match.group(1));
                info.put(HOST, match.group(2));
                info.put(MAX_HOPS, match.group(3));
                info.put(PACKETS_BYTES, match.group(4));
            } else {
                Logger.w(sTag, "\"" + lines[0] + "\" Not Matched");
            }
        }
        traceroute.put(INFO, info);
        
        Pattern reply = Pattern.compile("\\s*(\\d+)\\s+(\\S+) \\((\\S+)\\)\\s+(\\S+) ms");
        Pattern empty_reply = Pattern.compile("\\s*(\\d+)\\s+\\*");
        JSONArray hops_list = new JSONArray();
        for (int i = 1; i < lines.length; ++i) {
            JSONObject hop = new JSONObject();
            hop.put(LINE, lines[i]);
            hop.put(HOP_NUMBER, null);
            hop.put(HOST_NAME, null);
            hop.put(HOST, null);
            hop.put(RTT, null);
            hop.put(UNKNOWN, null);
            Matcher match = reply.matcher(lines[i]);
            if (match != null && match.find()) {
                hop.put(HOP_NUMBER, match.group(1));
                hop.put(HOST_NAME, match.group(2));
                hop.put(HOST, match.group(3));
                hop.put(RTT, match.group(4));
            } else {
                match = empty_reply.matcher(lines[i]);
                if (match != null && match.find()) {
                    hop.put(HOP_NUMBER, match.group(1));
                    hop.put(UNKNOWN, "*");
                } else {
                    Logger.w(sTag, "\"" + lines[i] + "\" Not Matched");
                }
            }
            hops_list.add(hop);
        }
        traceroute.put(HOPS, hops_list);

        traceroute.put(RET, retVal);
        traceroute.put(STDERR, error);
        traceroute.put(START_TIME, Utils.ms_to_s(startTime));
        traceroute.put(END_TIME, Utils.ms_to_s(endTime));
        
        return traceroute;
    }
}
