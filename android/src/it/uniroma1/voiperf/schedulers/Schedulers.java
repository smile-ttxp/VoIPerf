package it.uniroma1.voiperf.schedulers;

import it.uniroma1.voiperf.R;
import it.uniroma1.voiperf.logging.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Set;

import android.content.Context;

public class Schedulers {
    
    public static final String STATUS_UPDATE_ACTION = Schedulers.class.getPackage()
                                                             .getName() + ".STATUS_UPDATE_ACTION";
    public static final String EXTRA_NEXT_MEASUREMENT = Schedulers.class.getPackage()
                                                             .getName() + ".EXTRA_NEXT_MEASUREMENT";
    public static final String EXTRA_CURRENT_STATUS = Schedulers.class.getPackage()
                                                             .getName() + ".EXTRA_CURRENT_STATUS";
    
    private static final String sTag = Schedulers.class.getName();
    
    @SuppressWarnings("rawtypes")
    private static final HashMap<String, Class> sSchedulerTypes = new HashMap<String, Class>();
    static {
        sSchedulerTypes.put(FixedRepeatScheduler.class.getSimpleName(), FixedRepeatScheduler.class);
    }
    
    private static final HashMap<String, Integer> sSchedulerTitle = new HashMap<String, Integer>();
    static {
        sSchedulerTitle.put(FixedRepeatScheduler.class.getSimpleName(),
                                        R.string.fixedRepeatSchedulerTitle);
    }
    
    private static final HashMap<String, Integer> sSchedulerSummary =
                                    new HashMap<String, Integer>();
    static {
        sSchedulerSummary.put(FixedRepeatScheduler.class.getSimpleName(),
                                        R.string.fixedRepeatSchedulerSummary);
    }
    
    public static Set<String> getTypes() {
        return sSchedulerTypes.keySet();
    }
    
    public static String getSchedulerTitle(Context context, String type) {
        return context.getString(sSchedulerTitle.get(type));
    }
    
    public static String getSchedulerSummary(Context context, String type) {
        return context.getString(sSchedulerSummary.get(type));
    }
    
    @SuppressWarnings("unchecked")
    public static Scheduler createScheduler(String type) {
        try {
            return (Scheduler) sSchedulerTypes.get(type)
                                            .getConstructor((Class[]) null)
                                            .newInstance((Object[]) null);
        } catch (NullPointerException e) {
            Logger.e(sTag, "Could not find scheduler with type " + type, e);
            throw new SchedulerException(e);
        } catch (IllegalArgumentException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        } catch (SecurityException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        } catch (InstantiationException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        } catch (IllegalAccessException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        } catch (InvocationTargetException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        } catch (NoSuchMethodException e) {
            Logger.e(sTag, e.getMessage(), e);
            throw new SchedulerException(e);
        }
    }
}
