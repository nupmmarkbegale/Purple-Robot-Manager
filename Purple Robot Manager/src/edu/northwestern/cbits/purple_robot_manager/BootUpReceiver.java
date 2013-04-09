package edu.northwestern.cbits.purple_robot_manager;

import edu.northwestern.cbits.purple_robot_manager.config.LegacyJSONConfigFile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class BootUpReceiver extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {
    	ManagerService.setupPeriodicCheck(context);
    	
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    	
    	Editor e = prefs.edit();
    	
    	e.putLong("system_last_boot", System.currentTimeMillis());
    	
    	e.commit();

    	LegacyJSONConfigFile.getSharedFile(context.getApplicationContext());
    }
}
