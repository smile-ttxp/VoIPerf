package it.uniroma1.voiperf.measurements;

import it.uniroma1.voiperf.Config;
import it.uniroma1.voiperf.Session;
import it.uniroma1.voiperf.logging.Logger;
import it.uniroma1.voiperf.results.Result;
import it.uniroma1.voiperf.schedulers.Scheduler;
import it.uniroma1.voiperf.util.NetworkStatusRecorder;
import it.uniroma1.voiperf.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import org.json.simple.JSONObject;

import android.content.Context;
import android.os.AsyncTask;

public class VoIPerfMeasurement extends AsyncTask<Void, Void, Void> {

    private static final String sTag = VoIPerfMeasurement.class.getName();
    
    private ArrayList<Result> mResults = new ArrayList<Result>();
    
    public static class Measurement {
        public final String measurementType;
        public final String traceFilename;
        
        public Measurement(String measurementType, String traceFilename) {
            this.measurementType = measurementType;
            this.traceFilename = traceFilename;
        }
    }
    
    /* TODO: This whole statically defined thing is horrible, can we do it in a nicer way? */
    public static final int MEASUREMENT_COST_KB = (int) (5 + 10 * ((50 + 50 + 20 + 20) / 8.0));
    
    private static Measurement[] sUploadMeasurements = {
        new Measurement(RandomTraceSender.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceSender.TYPENAME, "rtp_50Kbps_40ms.trace"),
        new Measurement(RandomTraceSender.TYPENAME, "random_real_50Kbps_40ms.trace"),
        new Measurement(RTPTraceSender.TYPENAME, "rtp_20Kbps_20ms.trace"),
        new Measurement(RandomTraceSender.TYPENAME, "random_real_20Kbps_20ms.trace"),
        new Measurement(RTPTraceSender.TYPENAME, "rtp_10Kbps_20ms.trace"),
        new Measurement(RandomTraceSender.TYPENAME, "random_real_10Kbps_20ms.trace")
    };
    
    private static Measurement[] sDownloadMeasurements = {
        new Measurement(RandomTraceReceiver.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceReceiver.TYPENAME, "rtp_50Kbps_40ms.trace"),
        new Measurement(RandomTraceReceiver.TYPENAME, "random_real_50Kbps_40ms.trace"),
        new Measurement(RTPTraceReceiver.TYPENAME, "rtp_20Kbps_20ms.trace"),
        new Measurement(RandomTraceReceiver.TYPENAME, "random_real_20Kbps_20ms.trace"),
        new Measurement(RTPTraceReceiver.TYPENAME, "rtp_10Kbps_20ms.trace"),
        new Measurement(RandomTraceReceiver.TYPENAME, "random_real_10Kbps_20ms.trace")
    };
    
    // New 30s seconds traces
    private static Measurement[] sUploadMeasurements10Kbps = {
        new Measurement(RandomTraceSender.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceSender.TYPENAME, "rtp_30s_10Kbps_20ms.trace"),
        new Measurement(RandomTraceSender.TYPENAME, "random_30s_10Kbps_20ms.trace")
    };
    
    private static Measurement[] sUploadMeasurements20Kbps = {
        new Measurement(RandomTraceSender.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceSender.TYPENAME, "rtp_30s_20Kbps_20ms.trace"),
        new Measurement(RandomTraceSender.TYPENAME, "random_30s_20Kbps_20ms.trace")
    };
    
    private static Measurement[] sDownloadMeasurements10Kbps = {
        new Measurement(RandomTraceReceiver.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceReceiver.TYPENAME, "rtp_30s_10Kbps_20ms.trace"),
        new Measurement(RandomTraceReceiver.TYPENAME, "random_30s_10Kbps_20ms.trace")
    };
    
    private static Measurement[] sDownloadMeasurements20Kbps = {
        new Measurement(RandomTraceReceiver.TYPENAME, "warm_up.trace"),
        new Measurement(RTPTraceReceiver.TYPENAME, "rtp_30s_20Kbps_20ms.trace"),
        new Measurement(RandomTraceReceiver.TYPENAME, "random_30s_20Kbps_20ms.trace")
    };
    
    
    private static Measurement[][] sTasks = {
            sUploadMeasurements10Kbps, 
            sDownloadMeasurements10Kbps,
            sUploadMeasurements20Kbps,
            sDownloadMeasurements20Kbps
    };
    
    private final Scheduler mScheduler;
    private final NetworkStatusRecorder mNetworkStatusRecorder;
    private final ArrayList<Measurement> mTasks;
    
    public static void runTaskNumber(Scheduler scheduler, int i) {
        VoIPerfMeasurement vm = new VoIPerfMeasurement(scheduler);
        Logger.i(sTag, "Running tasks set " + i % sTasks.length);
        for (Measurement task: sTasks[i % sTasks.length]) {
            vm.addTask(task);
        }
        vm.execute(null, null, null);
    }
    
    public VoIPerfMeasurement(Scheduler scheduler) {
        mScheduler = scheduler;
        mNetworkStatusRecorder = new NetworkStatusRecorder();
        mTasks = new ArrayList<Measurement>();
    }
    
    public void addTask(Measurement task) {
        mTasks.add(task);
    }
    
    @Override
    protected void onPreExecute() {
        Logger.d(sTag, "onPreExecute");
        
        // Take a chance to rotate log files now
        Logger.d(sTag, "checking whether log files have to be rotated");
        Logger.rotateIfNecessary();
        
        // Watch out, may be very expensive, so be sure to stop
        // recording after the measurement has finished. Recording
        // can be stopped on the background thread
        Logger.d(sTag, "start recording");
        mNetworkStatusRecorder.startRecording();
    }
    
    @Override
    protected Void doInBackground(Void... arg0) {
        Logger.d(sTag, "doInBackground");
    
        Utils.acquireWakeLock();
        
        Socket server = null;
        boolean connectionSucceded = false;
        boolean serverIsBusy = false;
        boolean measurementFailed = true;
        try {
            Context context = Session.getGlobalContext();
            
            // Connect to the server
            server = Measurements.connectToMeasurementServer();
            DataOutputStream dos = new DataOutputStream(server.getOutputStream());
            DataInputStream dis = new DataInputStream(server.getInputStream());
            connectionSucceded = true;
            
            // Check whether we can start a new measurement
            String response = dis.readLine();
            if (response.equals(Config.MEASUREMENT_SERVER_BUSY_MSG)) {
                Logger.i(sTag, "Measurement server busy, retrying later");
                serverIsBusy = true;
                return null;
            } else if (response.equals(Config.MEASUREMENT_SERVER_AVAILABLE_MSG) == false) {
                Logger.w(sTag, "Unknown measurement server message \"" + response + "\"");
                return null;
            }
            Logger.i(sTag, "Measurement server available, start measurement");
            
            // Send our info
            sendClientInfo(dos, server);
            
            // Perform a traceroute and find the first reachable hop
            Traceroute traceroute = new Traceroute(server.getInetAddress().getHostAddress());
            traceroute.run();
            String firstHop = traceroute.findFirstHop();
            if (firstHop != null) {
                Logger.i(sTag, "first hop is " + firstHop);
            } else {
                Logger.w(sTag, "failed to find the first hop!");
            }
            
            // Send traceroute results
            sendTracerouteResult(dos, traceroute);

            for (Measurement task: mTasks) {
                String traceFileName = context.getFileStreamPath(task.traceFilename)
                                              .getAbsolutePath();
                
                // Send the measurement type
                dos.writeBytes(task.measurementType + "\n");
  
                // Use the sender/receiver specified
                if (task.measurementType.equals(RTPTraceReceiver.TYPENAME)) {
                    addResult(fromTraceStatistics(
                         RTPTraceReceiver.receiveTrace(dos, dis, traceFileName, firstHop), "Down"));
                } else if (task.measurementType.equals(RandomTraceReceiver.TYPENAME)) {
                    addResult(fromTraceStatistics(
                      RandomTraceReceiver.receiveTrace(dos, dis, traceFileName, firstHop), "Down"));
                } else if (task.measurementType.equals(RTPTraceSender.TYPENAME)) {
                    addResult(fromTraceStatistics(
                                RTPTraceSender.sendTrace(dos, dis, traceFileName, firstHop), "Up"));
                } else if (task.measurementType.equals(RandomTraceSender.TYPENAME)) {
                    addResult(fromTraceStatistics(
                            RandomTraceSender.sendTrace(dos, dis, traceFileName, firstHop), "Up"));
                }
            }
            
            // Tell the server that we finished sending traces
            dos.writeBytes(Config.MEASUREMENTS_END_MESSAGE + "\n");
            
            // Send the recorded network status to the server
            mNetworkStatusRecorder.stopRecording();
            sendRecordedNetworkStatuses(dos);
            
            measurementFailed = false;
            
        } catch (Exception e) {
            Logger.e(sTag, "Measurement failed: " + e.getMessage(), e);
        } finally {
            Measurements.closeMeasurementServerConnection(server);
            mNetworkStatusRecorder.stopRecording(); // We do it again in case of Exceptions
            mScheduler.measurementFinished(connectionSucceded, serverIsBusy, measurementFailed);
            Utils.releaseWakeLock();
            writeResultsToDatabase();
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private void sendClientInfo(DataOutputStream dos, Socket connection)  throws IOException {
        
        JSONObject info = new JSONObject();
        info.put("device_info", Session.getPhoneStatus().getDeviceInfo());
        info.put("phone_status", Session.getPhoneStatus().getPhoneStatus());
        info.put("network_status", Session.getNetworkStatus().getNetworkStatus());
        
        JSONObject address = new JSONObject();
        address.put("IP", connection.getLocalAddress().toString());
        address.put("port", connection.getLocalPort());
        info.put("client_local_address", address);
        
        info.put("preferences", Utils.dumpPreferences());
        
        String infoString = info.toJSONString();
        dos.writeBytes(infoString.length() + "\n");
        dos.writeBytes(infoString);
        Logger.i(sTag, "client info sent");
    }
    
    private void sendTracerouteResult(DataOutputStream dos, Traceroute traceroute)
        throws IOException {
        if (traceroute == null || traceroute.getResult() == null) {
            dos.writeBytes("0\n");
            return;
        }
        byte[] result = Utils.compressString(traceroute.getResult().toJSONString());
        dos.writeBytes(result.length + "\n");
        dos.write(result);
    }
    
    private void sendRecordedNetworkStatuses(DataOutputStream dos)
        throws IOException {
        if (mNetworkStatusRecorder == null) {
            dos.writeBytes("0\n");
            return;
        }
        byte[] data = Utils.compressString(mNetworkStatusRecorder.getResult().toJSONString());
        dos.writeBytes(data.length + "\n");
        dos.write(data);
    }
    
    private void addResult(Result result) {
    	if (result == null) {
    		Logger.d(sTag, "Skiping null result");
    		return;
    	}
    	mResults.add(result);
    }
    
    private Result fromTraceStatistics(TraceStatistics ts, String type) {
        if (ts == null) {
            return null;
        }
        Result result = new Result();
        result.setAverageJitter(ts.getAverageJitter());
        result.setType(type);
        result.setRate(ts.getRate());
        result.setTimestamp(System.currentTimeMillis()/1000);
        result.setMOS(ts.getMOS());
        result.setPacketLoss(ts.getPacketLoss()); 
        return result;
    }
    
    private void writeResultsToDatabase () {
    	Logger.d(sTag, "writeResultsToDatabase()");
    	for (Result result : mResults) {
    		Session.getDatabaseHandler().insertResult(result);
    	}
    }
}
