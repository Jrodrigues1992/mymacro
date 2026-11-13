# Prepara o projeto Android gerado pelo Capacitor para o MyMacro.
import os, re, shutil, glob
from PIL import Image

ANDROID = 'android'
JAVA = os.path.join(ANDROID, 'app/src/main/java/com/jean/mymacro')
RES = os.path.join(ANDROID, 'app/src/main/res')
os.makedirs(JAVA, exist_ok=True)

for f in glob.glob('native/*.java'):
    shutil.copy(f, os.path.join(JAVA, os.path.basename(f)))
os.makedirs(os.path.join(RES, 'xml'), exist_ok=True)
shutil.copy('native/accessibility_config.xml', os.path.join(RES, 'xml/accessibility_config.xml'))
os.makedirs(os.path.join(RES, 'values'), exist_ok=True)
shutil.copy('native/macro_strings.xml', os.path.join(RES, 'values/macro_strings.xml'))

mp = os.path.join(ANDROID, 'app/src/main/AndroidManifest.xml')
m = open(mp, encoding='utf-8').read()

PERMS = '''
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
'''
COMP = '''
        <service android:name=".MacroService" android:exported="false" android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="macro_automation" />
        </service>
        <service android:name=".TapAccessibilityService" android:exported="true"
            android:label="MyMacro" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_config" />
        </service>
        <receiver android:name=".AlarmReceiver" android:exported="false" />
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
'''
assert '<application' in m and '</application>' in m, 'manifesto inesperado'
m = m.replace('<application', PERMS + '    <application', 1)
m = m.replace('</application>', COMP + '    </application>', 1)
open(mp, 'w', encoding='utf-8').write(m)

base = Image.open('icon-512.png').convert('RGBA')
for d, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    folder = os.path.join(RES, 'mipmap-' + d)
    os.makedirs(folder, exist_ok=True)
    img = base.resize((px, px), Image.LANCZOS)
    img.save(os.path.join(folder, 'ic_launcher.png'))
    img.save(os.path.join(folder, 'ic_launcher_round.png'))
shutil.rmtree(os.path.join(RES, 'mipmap-anydpi-v26'), ignore_errors=True)
print('Projeto Android preparado.')
