# Wi-Fi Allowlist APK

Standalone, on-device Wi-Fi management app. No MDM server is required.

## Android compatibility

| Android | API | Behavior |
|---|---:|---|
| Android 9 | 28 | Limited mode only |
| Android 10 | 29 | Limited mode only |
| Android 11 | 30 | Limited mode only |
| Android 12 / 12L | 31–32 | Limited mode only |
| Android 13 and newer | 33+ | System-enforced SSID allowlist |

### Android 13+

Uses `DevicePolicyManager.setWifiSsidPolicy()`. Android only permits connections to SSIDs in the allowlist and disconnects from the current network if it is not allowed.

### Android 9–12: limited compatibility mode

Uses the Device Owner user restriction `UserManager.DISALLOW_CONFIG_WIFI`. This prevents the user from changing Wi-Fi access points through system Settings. It is **not** an SSID allowlist: it does not guarantee that previously saved non-listed networks will not auto-connect, and it cannot guarantee blocking connections initiated through every other system entry point or app.

This limitation is imposed by Android APIs; the app clearly labels the mode in its UI.

## Device Owner requirement

Both modes require the app to be provisioned as **Device Owner**. A normal install or ordinary device-admin activation is not enough. Setting Device Owner commonly requires a fresh/factory-reset device. Back up data first.

Install APK:

```bash
adb install app-debug.apk
```

Provision on a compatible, freshly initialized device:

```bash
adb shell dpm set-device-owner com.local.wifiallowlist/.AdminReceiver
```

Open the app, add SSID names, then enable the mode available for that Android version.

## Build

Every push to `main` runs the **Build Android APK** workflow. The artifact is named `wifi-allowlist-debug-apk` and contains `app-debug.apk`.

## Privacy

The app stores the SSID list locally using Android app preferences. It does not request Internet permission or upload the list.
