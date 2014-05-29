package it.uniroma1.voiperf.schedulers;

public class SchedulerException extends RuntimeException {

    private static final long serialVersionUID = 6092686292840530254L;

    public SchedulerException(String message) {
        super(message);
    }

    public SchedulerException(Exception e) {
        super(e);
    }
}
