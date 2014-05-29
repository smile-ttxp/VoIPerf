package it.uniroma1.voiperf.schedulers;

public interface Scheduler {

    public abstract void start();

    public abstract void stop();
    
    public abstract boolean isRunning();
    
    public abstract void measurementFinished(boolean connectionSucceded,
                                             boolean serverIsBusy,
                                             boolean measurementFailed);
        
}
