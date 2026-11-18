# Wi-Fi Allowlist APK

A standalone Android app for managing an SSID allowlist locally on one device.

## Build

Every push to `main` starts the GitHub Actions workflow **Build Android APK**. You can also start it manually from the Actions tab. When the build finishes successfully, open the run and download the artifact named `wifi-allowlist-debug-apk`; it contains `app-debug.apk`.

## Device requirements and important limitation

- Android 13 (API 33) or newer.
- The app must be provisioned as the **Device Owner** before it can apply the system Wi-Fi SSID policy. A normal APK install or ordinary device-admin activation is not enough.
- The allowlist is enforced by Android's `DevicePolicyManager.setWifiSsidPolicy()`. The system only permits SSIDs in the allowlist and disconnects from the current network when it is outside the allowlist.
- Add the SSID of the currently connected network before enabling the policy, or the device may disconnect from Wi-Fi.
- The policy matches SSID names, not router BSSIDs. Different access points with the same SSID are treated as the same network name.
- Setting Device Owner is a device-provisioning operation and commonly requires a fresh or factory-reset device. Back up your data first.

## Install and provision (ADB)

Install the APK:

```bash
adb install app-debug.apk
```

Then, on a compatible freshly provisioned device, set this package as Device Owner:

```bash
adb shell dpm set-device-owner com.local.wifiallowlist/.AdminReceiver
```

Open **Wi-Fi 白名单**, add the allowed SSIDs, and select **启用系统白名单**.

## Project source

The complete Android Studio project is archived at `source/WifiAllowlist-source.zip` in this repository. The archive contains its own Chinese README with more detailed setup notes.
