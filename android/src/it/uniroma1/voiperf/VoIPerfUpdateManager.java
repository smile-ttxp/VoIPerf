package it.uniroma1.voiperf;

import it.uniroma1.versionmanager.AppVersionManager;
import android.app.Activity;

public class VoIPerfUpdateManager {
    
    private AppVersionManager  mAppVersionManager;
   
    public VoIPerfUpdateManager() {
        init();     
    }
    
    public void checkUpdate(Activity activity) {
        mAppVersionManager.setActivity(activity); 
        mAppVersionManager.checkVersion();
    } 
    
    private void init() {
        mAppVersionManager = new AppVersionManager();
        mAppVersionManager.setVersionContentUrl(Config.VERSION_CONTENT_URL);
        mAppVersionManager.setUpdateNowLabel(Config.UPDATE_NOW_LABEL);
        mAppVersionManager.setRemindMeLaterLabel(Config.REMIND_ME_LATER_LABEL);
        mAppVersionManager.setReminderTimer(Config.REMINDER_TIMER);
    }
}