package com.jean.mymacro;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MacroPlugin.class);
        super.onCreate(savedInstanceState);
        MacroScheduler.ensureRunning(this);
    }
}
