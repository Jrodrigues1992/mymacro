package com.jean.mymacro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        MacroScheduler.scheduleAll(c);
        MacroScheduler.ensureRunning(c);
    }
}
