package com.jean.mymacro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.content.ContextCompat;

public class MacroService extends Service {
    static boolean running = false;
    private BroadcastReceiver rx;
    private ConnectivityManager.NetworkCallback cb;
    private int lastLevel = -1;
    private boolean wifiUp = false;
    private long startedAt;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startFg();
        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("mymacro_service", "MyMacro ativo", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, "mymacro_service");
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        b.setContentIntent(PendingIntent.getActivity(this, 0, open, fl));
        b.setSmallIcon(android.R.drawable.ic_popup_sync);
        b.setContentTitle("MyMacro esta ativo");
        b.setContentText("Observando os gatilhos das suas macros");
        b.setOngoing(true);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, b.build());
            }
        } catch (Throwable e) {
            MacroEngine.log(this, "Falha ao iniciar em primeiro plano: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startedAt = System.currentTimeMillis();
        startFg();
        final Context c = getApplicationContext();

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent in) {
                String a = in.getAction();
                if (a == null) return;
                if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                    MacroEngine.log(c, "Carregador conectado");
                    MacroEngine.fire(c, "charger_on", null);
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                    MacroEngine.log(c, "Carregador desconectado");
                    MacroEngine.fire(c, "charger_off", null);
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    int lvl = in.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = in.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (lvl < 0 || scale <= 0) return;
                    int pct = lvl * 100 / scale;
                    if (lastLevel >= 0 && pct != lastLevel) {
                        MacroEngine.fireBattery(c, lastLevel, pct);
                    }
                    lastLevel = pct;
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a) || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                    String name = "";
                    try {
                        BluetoothDevice d = in.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (d != null && d.getName() != null) name = d.getName();
                    } catch (Throwable e) { /* sem permissao */ }
                    boolean on = BluetoothDevice.ACTION_ACL_CONNECTED.equals(a);
                    MacroEngine.log(c, "Bluetooth " + (on ? "conectado: " : "desconectado: ") + name);
                    MacroEngine.fire(c, on ? "bt_on" : "bt_off", name);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        ContextCompat.registerReceiver(this, rx, f, ContextCompat.RECEIVER_EXPORTED);

        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest req = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
            cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    if (!wifiUp) {
                        wifiUp = true;
                        if (System.currentTimeMillis() - startedAt > 4000) {
                            MacroEngine.log(c, "Wi-Fi conectado");
                            MacroEngine.fire(c, "wifi_on", null);
                        }
                    }
                }

                @Override
                public void onLost(Network n) {
                    if (wifiUp) {
                        wifiUp = false;
                        MacroEngine.log(c, "Wi-Fi desconectado");
                        MacroEngine.fire(c, "wifi_off", null);
                    }
                }
            };
            cm.registerNetworkCallback(req, cb);
        } catch (Throwable e) {
            MacroEngine.log(c, "Wi-Fi nao observado: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (rx != null) unregisterReceiver(rx); } catch (Throwable e) { /* ignora */ }
        try {
            if (cb != null) ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(cb);
        } catch (Throwable e) { /* ignora */ }
        super.onDestroy();
    }
}
