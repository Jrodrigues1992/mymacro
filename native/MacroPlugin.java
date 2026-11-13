package com.jean.mymacro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@CapacitorPlugin(name = "Macro")
public class MacroPlugin extends Plugin {

    private boolean granted(String p) {
        return ActivityCompat.checkSelfPermission(getContext(), p) == PackageManager.PERMISSION_GRANTED;
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        Context c = getContext();
        JSObject o = new JSObject();
        o.put("accessibility", TapAccessibilityService.instance != null);
        o.put("overlay", Settings.canDrawOverlays(c));
        o.put("notifications", Build.VERSION.SDK_INT < 33 || granted("android.permission.POST_NOTIFICATIONS"));
        o.put("sms", granted(Manifest.permission.SEND_SMS));
        o.put("bluetooth", Build.VERSION.SDK_INT < 31 || granted("android.permission.BLUETOOTH_CONNECT"));
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        o.put("battery", pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        o.put("service", MacroService.running);
        call.resolve(o);
    }

    @PluginMethod
    public void askPermissions(PluginCall call) {
        List<String> need = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33 && !granted("android.permission.POST_NOTIFICATIONS")) need.add("android.permission.POST_NOTIFICATIONS");
        if (!granted(Manifest.permission.SEND_SMS)) need.add(Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= 31 && !granted("android.permission.BLUETOOTH_CONNECT")) need.add("android.permission.BLUETOOTH_CONNECT");
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(getActivity(), need.toArray(new String[0]), 77);
        }
        call.resolve();
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        String what = call.getString("what", "");
        String pkg = getContext().getPackageName();
        if ("overlay".equals(what)) open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg)));
        else if ("accessibility".equals(what)) open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        else if ("battery".equals(what)) open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg)));
        else if ("app".equals(what)) open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        call.resolve();
    }

    @PluginMethod
    public void saveMacros(PluginCall call) {
        String json = call.getString("json", "[]");
        MacroEngine.save(getContext(), json);
        MacroScheduler.scheduleAll(getContext());
        MacroScheduler.ensureRunning(getContext());
        call.resolve();
    }

    @PluginMethod
    public void loadMacros(PluginCall call) {
        JSObject o = new JSObject();
        o.put("json", MacroEngine.load(getContext()).toString());
        call.resolve(o);
    }

    @PluginMethod
    public void getLog(PluginCall call) {
        JSObject o = new JSObject();
        o.put("json", MacroEngine.loadLog(getContext()));
        call.resolve(o);
    }

    @PluginMethod
    public void clearLog(PluginCall call) {
        MacroEngine.clearLog(getContext());
        call.resolve();
    }

    @PluginMethod
    public void runMacro(PluginCall call) {
        String id = call.getString("id", "");
        JSONObject m = MacroEngine.find(getContext(), id);
        if (m == null) { call.reject("Macro nao encontrada. Salve antes de testar."); return; }
        MacroEngine.runMacro(getContext(), m);
        call.resolve();
    }

    @PluginMethod
    public void listApps(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        final List<String[]> apps = new ArrayList<String[]>();
        for (ResolveInfo r : list) {
            apps.add(new String[]{String.valueOf(r.loadLabel(pm)), r.activityInfo.packageName});
        }
        Collections.sort(apps, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); }
        });
        JSONArray arr = new JSONArray();
        try {
            for (String[] a : apps) {
                JSONObject o = new JSONObject();
                o.put("name", a[0]);
                o.put("pkg", a[1]);
                arr.put(o);
            }
        } catch (Exception e) { /* ignora */ }
        JSObject ret = new JSObject();
        ret.put("json", arr.toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void startRecording(PluginCall call) {
        Context c = getContext();
        if (TapAccessibilityService.instance == null) { call.reject("Ative o servico de Acessibilidade do MyMacro primeiro."); return; }
        if (!Settings.canDrawOverlays(c)) { call.reject("Permita 'Exibir sobre outros apps' primeiro."); return; }
        if (Recorder.active()) { call.reject("Ja esta gravando."); return; }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        open(home);
        Recorder.start(c, new Recorder.Done() {
            @Override
            public void onDone(JSONArray strokes) {
                Context cc = getContext();
                Intent back = cc.getPackageManager().getLaunchIntentForPackage(cc.getPackageName());
                if (back != null) {
                    back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try { cc.startActivity(back); } catch (Throwable e) { /* ignora */ }
                }
                JSObject o = new JSObject();
                o.put("strokes", strokes.toString());
                notifyListeners("recorded", o, true);
            }
        });
        call.resolve();
    }
}
