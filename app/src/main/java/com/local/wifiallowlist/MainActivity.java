package com.local.wifiallowlist;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Local Wi-Fi allowlist manager.
 * Android 13+: system-enforced SSID allowlist.
 * Android 9-12: limited Device Owner mode that blocks Wi-Fi access-point
 * changes through Settings; it is NOT a strict SSID allowlist.
 */
public final class MainActivity extends Activity {
    private static final String PREFS = "wifi_allowlist";
    private static final String KEY_SSIDS = "ssids";
    private static final String PACKAGE_NAME = "com.local.wifiallowlist";

    private DevicePolicyManager dpm;
    private ComponentName admin;
    private EditText ssidInput;
    private TextView statusText;
    private LinearLayout listContainer;
    private final Set<String> allowedSsids = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        loadSsids();
        buildUi();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        scrollView.addView(root);

        TextView title = text("Wi-Fi 白名单", 26, Color.rgb(25, 35, 52), true);
        root.addView(title);
        String subtitleText = Build.VERSION.SDK_INT >= 33
                ? "系统级限制：只允许连接白名单中的 Wi-Fi"
                : "旧版兼容模式：限制通过系统设置更改 Wi-Fi，非严格白名单";
        TextView subtitle = text(subtitleText, 14, Color.rgb(90, 100, 115), false);
        LinearLayout.LayoutParams subParams = params(-1, -2);
        subParams.topMargin = dp(6);
        root.addView(subtitle, subParams);

        statusText = text("正在读取设备状态…", 14, Color.rgb(30, 45, 65), false);
        statusText.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(18);
        root.addView(statusText, statusParams);

        TextView addTitle = text("添加允许的 Wi-Fi 名称（SSID）", 16, Color.rgb(25, 35, 52), true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(22);
        root.addView(addTitle, headingParams);

        ssidInput = new EditText(this);
        ssidInput.setSingleLine(true);
        ssidInput.setTextSize(16);
        ssidInput.setHint("例如：Home_WiFi");
        ssidInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputParams = params(-1, -2);
        inputParams.topMargin = dp(10);
        root.addView(ssidInput, inputParams);

        Button addButton = button("添加到白名单");
        LinearLayout.LayoutParams addParams = params(-1, -2);
        addParams.topMargin = dp(8);
        root.addView(addButton, addParams);
        addButton.setOnClickListener(v -> addSsid());
        ssidInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSsid();
                return true;
            }
            return false;
        });

        TextView listTitle = text("已添加的网络", 16, Color.rgb(25, 35, 52), true);
        LinearLayout.LayoutParams listHeadingParams = params(-1, -2);
        listHeadingParams.topMargin = dp(22);
        root.addView(listTitle, listHeadingParams);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), dp(8), dp(12), dp(8));
        listContainer.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams listParams = params(-1, -2);
        listParams.topMargin = dp(8);
        root.addView(listContainer, listParams);
        renderList();

        Button applyButton = button(Build.VERSION.SDK_INT >= 33 ? "启用系统 Wi-Fi 白名单" : "启用旧版 Wi-Fi 受限模式");
        LinearLayout.LayoutParams applyParams = params(-1, -2);
        applyParams.topMargin = dp(22);
        root.addView(applyButton, applyParams);
        applyButton.setOnClickListener(v -> applyAllowlist());

        Button clearButton = button("关闭 Wi-Fi 限制");
        LinearLayout.LayoutParams clearParams = params(-1, -2);
        clearParams.topMargin = dp(8);
        root.addView(clearButton, clearParams);
        clearButton.setOnClickListener(v -> clearPolicy());

        String noteText = Build.VERSION.SDK_INT >= 33
                ? "Android 13+：系统会阻止连接名单之外的 SSID，并在当前网络不符合策略时断开。启用前先把当前 Wi-Fi 加入名单。"
                : "Android 9–12 兼容限制：启用后会禁止用户通过系统设置更改 Wi-Fi 接入点，但不能阻止已保存的其他 Wi-Fi 自动连接，也不能保证阻止其他应用或系统入口连接非白名单网络。此模式不是真正的 SSID 白名单。";
        TextView note = text(noteText, 12, Color.rgb(90, 100, 115), false);
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(14);
        root.addView(note, noteParams);

        Button copyCommand = button("复制 ADB 设备所有者设置命令");
        LinearLayout.LayoutParams copyParams = params(-1, -2);
        copyParams.topMargin = dp(18);
        root.addView(copyCommand, copyParams);
        copyCommand.setOnClickListener(v -> copySetupCommand());

        Button releaseOwner = button("解除设备所有者（测试用途）");
        LinearLayout.LayoutParams releaseParams = params(-1, -2);
        releaseParams.topMargin = dp(8);
        root.addView(releaseOwner, releaseParams);
        releaseOwner.setOnClickListener(v -> confirmReleaseOwner());

        setContentView(scrollView);
    }

    private void addSsid() {
        String ssid = ssidInput.getText().toString();
        if (ssid.trim().isEmpty()) {
            toast("请输入 Wi-Fi 名称");
            return;
        }
        int byteLength = ssid.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > 32) {
            toast("SSID 最长为 32 个 UTF-8 字节");
            return;
        }
        if (allowedSsids.contains(ssid)) {
            toast("这个 Wi-Fi 已经在列表中");
            return;
        }
        allowedSsids.add(ssid);
        saveSsids();
        ssidInput.setText("");
        renderList();
        refreshStatus();
    }

    private void removeSsid(String ssid) {
        allowedSsids.remove(ssid);
        saveSsids();
        renderList();
        refreshStatus();
        toast("已删除。Android 13+ 请重新应用系统白名单以同步更改");
    }

    private void applyAllowlist() {
        if (!isDeviceOwner()) {
            showNotOwnerDialog();
            return;
        }
        if (allowedSsids.isEmpty()) {
            toast("请先至少添加一个 Wi-Fi 名称");
            return;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            applyModernAllowlist();
        } else {
            confirmLegacyLimitedMode();
        }
    }

    private void applyModernAllowlist() {
        try {
            // Remove a possible legacy restriction if this device was updated from an older build.
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
            Api33WifiPolicy.setAllowlist(dpm, allowedSsids);
            refreshStatus();
            toast("系统 Wi-Fi SSID 白名单已启用");
        } catch (SecurityException e) {
            toast("系统拒绝设置策略：请确认本应用是设备所有者");
        } catch (Exception e) {
            toast("设置失败：" + safeMessage(e));
        }
    }

    private void confirmLegacyLimitedMode() {
        new AlertDialog.Builder(this)
                .setTitle("启用 Android 9–12 兼容模式？")
                .setMessage("旧版 Android 没有系统级 SSID 白名单接口。此模式只会禁止用户通过系统设置更改 Wi-Fi 接入点；已经保存的其他 Wi-Fi 仍可能自动连接，也可能通过其他系统入口或应用连接。它不能保证设备只连接此列表。\n\n是否继续启用这项受限管理？")
                .setNegativeButton("取消", null)
                .setPositiveButton("启用受限模式", (dialog, which) -> {
                    try {
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                        refreshStatus();
                        toast("旧版 Wi-Fi 受限模式已启用（并非严格白名单）");
                    } catch (Exception e) {
                        toast("启用失败：" + safeMessage(e));
                    }
                })
                .show();
    }

    private void clearPolicy() {
        if (!isDeviceOwner()) {
            showNotOwnerDialog();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("关闭 Wi-Fi 限制？")
                .setMessage("系统将恢复 Wi-Fi 接入点配置权限。保存在本机的名单不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("关闭限制", (dialog, which) -> {
                    try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            Api33WifiPolicy.clear(dpm);
                        }
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                        refreshStatus();
                        toast("Wi-Fi 限制已关闭");
                    } catch (Exception e) {
                        toast("关闭失败：" + safeMessage(e));
                    }
                })
                .show();
    }

    private void refreshStatus() {
        if (statusText == null) return;
        StringBuilder status = new StringBuilder();
        status.append("Android：").append(Build.VERSION.RELEASE)
                .append("（API ").append(Build.VERSION.SDK_INT).append("）\n");
        status.append("设备所有者：").append(isDeviceOwner() ? "已启用" : "未启用").append("\n");
        status.append("本地名单：").append(allowedSsids.size()).append(" 个网络\n");

        if (!isDeviceOwner()) {
            status.append("系统限制：尚未设置");
        } else if (Build.VERSION.SDK_INT >= 33) {
            try {
                status.append("系统限制：").append(Api33WifiPolicy.describe(dpm));
            } catch (Exception e) {
                status.append("系统限制：读取失败");
            }
        } else {
            try {
                boolean restricted = dpm.getUserRestrictions(admin)
                        .getBoolean(UserManager.DISALLOW_CONFIG_WIFI, false);
                status.append(restricted
                        ? "系统限制：旧版受限模式已启用（非严格 SSID 白名单）"
                        : "系统限制：旧版受限模式未启用");
            } catch (Exception e) {
                status.append("系统限制：旧版状态读取失败");
            }
        }
        statusText.setText(status.toString());
    }

    private void renderList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        if (allowedSsids.isEmpty()) {
            TextView empty = text("尚未添加 Wi-Fi", 14, Color.rgb(110, 118, 130), false);
            empty.setPadding(0, dp(8), 0, dp(8));
            listContainer.addView(empty);
            return;
        }
        for (String ssid : new TreeSet<>(allowedSsids)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(ssid, 15, Color.rgb(30, 40, 55), false);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            Button remove = button("删除");
            row.addView(remove, params(-2, -2));
            remove.setOnClickListener(v -> removeSsid(ssid));
            listContainer.addView(row, params(-1, -2));
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(235, 237, 240));
            LinearLayout.LayoutParams dividerParams = params(-1, dp(1));
            dividerParams.topMargin = dp(6);
            dividerParams.bottomMargin = dp(6);
            listContainer.addView(divider, dividerParams);
        }
    }

    private void copySetupCommand() {
        String command = "adb shell dpm set-device-owner " + PACKAGE_NAME + "/.AdminReceiver";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Device Owner 命令", command));
        toast("命令已复制；需连接电脑使用 ADB 执行");
    }

    private void showNotOwnerDialog() {
        String command = "adb shell dpm set-device-owner " + PACKAGE_NAME + "/.AdminReceiver";
        new AlertDialog.Builder(this)
                .setTitle("尚未取得设备所有者权限")
                .setMessage("仅开启普通“设备管理员”不够。请先在电脑安装 ADB，连接手机并执行：\n\n" + command
                        + "\n\n设置设备所有者通常需要设备处于刚恢复出厂设置后的初始状态。请先备份数据。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void confirmReleaseOwner() {
        if (!isDeviceOwner()) {
            toast("本应用当前不是设备所有者");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("解除设备所有者？")
                .setMessage("将先清除本应用设置的 Wi-Fi 限制，再解除设备所有者。")
                .setNegativeButton("取消", null)
                .setPositiveButton("解除", (dialog, which) -> {
                    try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            Api33WifiPolicy.clear(dpm);
                        }
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                        dpm.clearDeviceOwnerApp(getPackageName());
                        refreshStatus();
                        toast("设备所有者已解除");
                    } catch (Exception e) {
                        toast("解除失败：" + safeMessage(e));
                    }
                })
                .show();
    }

    private boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
    }

    private void loadSsids() {
        Set<String> saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_SSIDS, Collections.emptySet());
        allowedSsids.clear();
        if (saved != null) allowedSsids.addAll(saved);
    }

    private void saveSsids() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_SSIDS, new HashSet<>(allowedSsids))
                .apply();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        return button;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName() : message;
    }
}
