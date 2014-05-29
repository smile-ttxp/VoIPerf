package it.uniroma1.voiperf;

public interface MeasurementServiceClient {

    /**
     * Ask the calling activity form to indicate that the service has been stopped.
     */
    public void onBackgroundServiceStopped();

    /**
     * Ask the calling activity form to indicate that the service has been started.
     */
    public void onBackgroundServiceStarted();
}
