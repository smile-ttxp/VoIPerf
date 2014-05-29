package it.uniroma1.voiperf;

import it.uniroma1.voiperf.schedulers.FixedRepeatScheduler;

/**
 * Constants used across all the project.
 */
public final class Config {

    /* Default values */
    public static final boolean START_ON_BOOT_DEFAULT = true;
    public static final boolean FOREGROUND_SERVICE_RUNNING_DEFAULT = true;
    public static final String SCHEDULER_TYPE_DEFAULT = FixedRepeatScheduler.class.getName();
    public static final int FIXED_REPEAT_SCHEDULER_PROFILE_DEFAULT = 2;
    public static final int FIXED_REPEAT_SCHEDULER_BATTERY_MIN_PREF_DEFAULT = 5;
    
    /* FixedRepeatScheduler */
    public static final String FIXED_REPEAT_SCHEDULER_PROFILE_PREF_KEY = "KEY_FIXED_SCHEDULER_PROFILE";
    public static final String FIXED_REPEAT_SCHEDULER_BATTERY_MIN_PREF_KEY = "KEY_FIXED_SCHEDULER_BATTERY_THRESHOLD";
    public static final String FIXED_REPEAT_SCHEDULER_NEXT_TIME_KEY = "KEY_FIXED_SCHEDULER_NEXT_TIME";
    public static final int FIXED_REPEAT_SCHEDULER_BACKOFF_SECONDS = 300;
    public static final int FIXED_REPEAT_SCHEDULER_RETRY_SECONDS =  900;
    public static final int FIXED_REPEAT_SCHEDULER_BATTERY_LOW_RETRY_SECONDS = 900;
    public static final String[] FIXED_REPEAT_SCHEDULER_PROFILES = new String[] {
        "Standard frequency", "Low frequency",
        "Very low frequency", "Every minute (for debugging purposes)"};
    public static final int[] FIXED_REPEAT_SCHEDULER_PROFILE_VALUES = new int[] {1,2,3,4};
    public static final String[] FIXED_REPEAT_SCHEDULER_BATTERY_THRESHOLDS = new String[] {
        "5%", "10%", "20%"};
    public static final int[] FIXED_REPEAT_SCHEDULER_BATTERY_THRESHOLDS_VALUES = new int[] {5, 10, 20};
    
    /* Global Preferences Keys */
    public static final String START_ON_BOOT_PREF_KEY = "KEY_START_ON_BOOT";
    public static final String SCHEDULER_TYPE_PREF_KEY = "KEY_SCHEDULER_TYPE";
    public static final String SCHEDULER_CONFIG_PREF_KEY = "KEY_SCHEDULER_CONFIG";
    public static final String MEASUREMENT_LOG_ROATE_SZ_KEY = "KEY_MEASUREMENT_LOG_ROTATE_SZ";
    public static final String SYSTEM_LOG_ROTATE_SZ_KEY = "KEY_SYSTEM_LOG_ROATE_SZ";
    public static final String FOREGROUND_SERVICE_RUNNING_KEY = "KEY_FOREGROUND_SERVICE_STARTED";
    
    /* Logger */
    public static final int SYSTEM_LOG_ROTATE_SZ_DEFAULT = 1048576;
    public static final String LAST_SYSTEM_LOG_FILE_ROTATION_KEY = "KEY_LAST_LOG_FILE_ROTATION";
    public static final int SYSTEM_LOG_ROTATE_SECONDS_DEFAULT = 3600;
    
    /* Measurement server */
    public static final String MEASUREMENT_SERVER_ADDRESS = "151.100.179.250";
    public static final int MEASUREMENT_SERVER_PORT = 12345;   

    /* VoIP Measurement */
    public static final String MEASUREMENT_SERVER_AVAILABLE_MSG = "OK";
    public static final String MEASUREMENT_SERVER_BUSY_MSG = "BUSY";
    public static final String TASKS_LIST_INDEX_PREF_KEY = "KEY_TASKS_LIST_INDEX";
    public static final int TRACE_SOCKET_WAIT_TIMEOUT_MILLIS = 10000;
    public static final int TRACE_SOCKET_READ_TIMEOUT_MILLIS = 3000;
    public static final int TRACE_SOCKET_SEND_BUFFER_SIZE = 1048576;
    public static final int TRACE_SOCKET_RECV_BUFFER_SIZE = 1048576;
    public static final int SENDER_THREAD_WAIT_MILLIS = 10000;
    public static final int MAX_TRACE_PACKET_SIZE = 1500;
    public static final String TRACE_END_MESSAGE = "QUIT";
    public static final String MEASUREMENTS_END_MESSAGE = "END";
    public static final String HOLE_PUNCHING_MESSAGE = "I HATE NAT";
    public static final int HOLE_PUNCHING_RETRIES = 3;
    public static final int HOLE_PUNCHING_WAIT_TIME_MILLIS = 100;
    
    /* Ping to the first hop */
    public static final int DISCOVERY_PING_COUNT = 5;
    public static final int FIRST_HOP_PING_PER_SECOND = 2;
    public static final int FIRST_HOP_PING_PACKET_SIZE = 8;
    public static final int FIRST_HOPE_PING_MIN_DURATION_SECONDS = 2;
    
    /* CheckVersion TODO: these must be in strings.xml!!!! */
    public static final String VERSION_CONTENT_URL = "http://151.100.179.250/voiperf_update/";
    public static final String UPDATE_NOW_LABEL = "Update now";
    public static final String REMIND_ME_LATER_LABEL = "Remind me later";
    public static final String IGNORE_THIS_VERSION_LABEL = "Ignore this version";
    public static final int REMINDER_TIMER = 30;
}
