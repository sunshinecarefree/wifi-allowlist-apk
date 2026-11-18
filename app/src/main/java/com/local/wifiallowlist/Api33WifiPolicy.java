package com.local.wifiallowlist;

import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.net.wifi.WifiSsid;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Isolated API 33+ calls so the main Activity can run on Android 9-12. */
final class Api33WifiPolicy {
    private Api33WifiPolicy() {}

    static void setAllowlist(DevicePolicyManager dpm, Set<String> names) {
        Set<WifiSsid> ssids = new HashSet<>();
        for (String name : names) {
            ssids.add(WifiSsid.fromBytes(name.getBytes(StandardCharsets.UTF_8)));
        }
        WifiSsidPolicy policy = new WifiSsidPolicy(
                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST, ssids);
        dpm.setWifiSsidPolicy(policy);
    }

    static void clear(DevicePolicyManager dpm) {
        dpm.setWifiSsidPolicy(null);
    }

    static String describe(DevicePolicyManager dpm) {
        WifiSsidPolicy current = dpm.getWifiSsidPolicy();
        if (current == null) return "未启用";
        if (current.getPolicyType() == WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST) {
            return "系统级 SSID 白名单已启用（" + current.getSsids().size() + " 个网络）";
        }
        return "当前为系统拒绝列表模式";
    }
}
