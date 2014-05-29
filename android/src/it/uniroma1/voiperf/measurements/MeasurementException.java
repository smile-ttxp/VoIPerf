package it.uniroma1.voiperf.measurements;

public class MeasurementException extends Exception {

    private static final long serialVersionUID = 7145843297386256662L;

    public MeasurementException(String message) {
        super(message);
    }
  
    public MeasurementException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
