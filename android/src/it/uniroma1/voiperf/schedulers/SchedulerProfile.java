package it.uniroma1.voiperf.schedulers;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import it.uniroma1.voiperf.logging.Logger;

public abstract class SchedulerProfile {

    private final static String sTag = SchedulerProfile.class.getName();
    
    private static SchedulerProfile[] sProfiles = {
      new SchedulerProfile("Very high", (12 * 3) + (3 * 2) + 12) {
        @Override
        public long nextMeasurementInterval(int hour) {
            if (8 <= hour && hour <= 20) {
                return TimeUnit.SECONDS.toMillis(60 * 20);
            } else if (20 < hour && hour <= 24) { 
                return TimeUnit.SECONDS.toMillis(60 * 30);
            } else {
                return TimeUnit.SECONDS.toMillis(60 * 60 * 2);
            }
        }
      },
      new SchedulerProfile("High", (16 * 2) + 12) {
          @Override
          public long nextMeasurementInterval(int hour) {
              if (8 <= hour && hour <= 24) {
                  return TimeUnit.SECONDS.toMillis(60 * 30);
              } else {
                  return TimeUnit.SECONDS.toMillis(60 * 60 * 2);
              }
          }
      },
      new SchedulerProfile("Medium", (12 * 2) + 4 + 4) {
          @Override
          public long nextMeasurementInterval(int hour) {
              if (8 <= hour && hour <= 20) {
                  return TimeUnit.SECONDS.toMillis(60 * 30);
              } else if (20 < hour && hour <= 24) {
                  return TimeUnit.SECONDS.toMillis(60 * 60);
              } else {
                  return TimeUnit.SECONDS.toMillis(60 * 60 * 2);
              }
          }
      },
      new SchedulerProfile("Low", 12 + 4) {
          @Override
          public long nextMeasurementInterval(int hour) {
              if (8 <= hour && hour <= 20) {
                  return TimeUnit.SECONDS.toMillis(60 * 60);
              } else {
                  return TimeUnit.SECONDS.toMillis(60 * 60 * 3);
              }
          }
      },
      new SchedulerProfile("Very Low", 8 + 3) {
          @Override
          public long nextMeasurementInterval(int hour) {
              if (8 <= hour && hour <= 20) {
                  return TimeUnit.SECONDS.toMillis(60 * 90);
              } else {
                  return TimeUnit.SECONDS.toMillis(60 * 60 * 4);
              }
          }
      },
      new SchedulerProfile("Every minute", 1440) {
          @Override
          public long nextMeasurementInterval(int hour) {
              return TimeUnit.SECONDS.toMillis(60);
          }
      }
    };
    
    public static SchedulerProfile getScheduler(int code) {
        Logger.d(sTag, "getScheduler(" + code + ")");
        try {
            Logger.d(sTag, "using a " + sProfiles[code].getName() + " scheduler profile");
            return sProfiles[code];
        } catch (ArrayIndexOutOfBoundsException e) {
            Logger.e(sTag, "BUG: unknown scheduler profile code "
                           + code + " defaulting to " + sProfiles[sProfiles.length / 2].getName());
            return sProfiles[sProfiles.length / 2];
        }
    }
    
    public static SchedulerProfile[] getProfiles() {
        return sProfiles;
    }
    
    private final String mName;
    private final int mNAtDay;
    
    public SchedulerProfile(String name, int nAtDay) {
        mName = name;
        mNAtDay = nAtDay;
    }
    
    public String getName() {
        return mName;
    }
    
    public int getNumberOfDailyMeasurements() {
        return mNAtDay;
    }
    
    public abstract long nextMeasurementInterval(int hour);
    
    public long nextMeasurementInterval() {
        return nextMeasurementInterval(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }
}