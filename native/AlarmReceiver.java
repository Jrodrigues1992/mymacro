package com.jean.mymacro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        if (id != null) {
            JSONObject m = MacroEngine.find(c, id);
            if (m != null && m.optBoolean("enabled", true)) MacroEngine.runMacro(c, m);
        }
        MacroScheduler.scheduleAll(c);
        MacroScheduler.ensureRunning(c);
    }
}
