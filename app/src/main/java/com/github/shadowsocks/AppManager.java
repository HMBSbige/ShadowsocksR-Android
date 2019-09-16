package com.github.shadowsocks;

import android.Manifest;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.github.shadowsocks.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AppManager extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    private static AppManager instance;

    private boolean receiverRegistered;
    private List<ProxiedApp> cachedApps;

    private HashSet<String> proxiedApps;
    private Toolbar toolbar;
    private Switch bypassSwitch;
    private RecyclerView appListView;
    private View loadingView;
    private AtomicBoolean appsLoading;
    private Handler handler;
    private Profile profile = ShadowsocksApplication.app.currentProfile();

    private void initProxiedApps() {
        initProxiedApps(profile.individual);
    }

    private void initProxiedApps(String str) {
        String[] split = str.split("\n");
        List<String> list = Arrays.asList(split);
        proxiedApps = new HashSet<>(list);
    }

    @Override
    protected void onDestroy() {
        instance = null;
        super.onDestroy();

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String proxiedAppString;
        switch (item.getItemId()) {
            case R.id.action_apply_all:
                List<Profile> profiles = ShadowsocksApplication.app.profileManager.getAllProfiles();
                if (profiles != null) {
                    proxiedAppString = profile.individual;
                    boolean in_proxyapp = profile.proxyApps;
                    boolean in_bypass = profile.bypass;
                    for (Profile p : profiles) {
                        p.individual = proxiedAppString;
                        p.bypass = in_bypass;
                        p.proxyApps = in_proxyapp;
                        ShadowsocksApplication.app.profileManager.updateProfile(p);
                    }
                    ToastUtils.showShort(R.string.action_apply_all);
                } else {
                    ToastUtils.showShort(R.string.action_export_err);
                }
                return true;
            case R.id.action_export:
                boolean bypass = profile.bypass;
                proxiedAppString = profile.individual;
                ClipData clip = ClipData.newPlainText(Constants.Key.individual, bypass + "\n" + proxiedAppString);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    ToastUtils.showShort(R.string.action_export_msg);
                }
                return true;
            case R.id.action_import:
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    CharSequence proxiedAppSequence = clipboard.getPrimaryClip().getItemAt(0).getText();
                    if (proxiedAppSequence != null) {
                        proxiedAppString = proxiedAppSequence.toString();
                        if (!TextUtils.isEmpty(proxiedAppString)) {
                            int i = proxiedAppString.indexOf('\n');
                            try {
                                String enabled;
                                String apps;
                                if (i < 0) {
                                    enabled = proxiedAppString;
                                    apps = "";
                                } else {
                                    enabled = proxiedAppString.substring(0, i);
                                    apps = proxiedAppString.substring(i + 1);
                                }

                                bypassSwitch.setChecked(Boolean.parseBoolean(enabled));
                                profile.individual = apps;
                                ShadowsocksApplication.app.profileManager.updateProfile(profile);
                                ToastUtils.showShort(R.string.action_import_msg);
                                appListView.setVisibility(View.GONE);
                                loadingView.setVisibility(View.VISIBLE);
                                initProxiedApps(apps);
                                reloadApps();
                                return true;
                            } catch (Exception e) {
                                ToastUtils.showShort(R.string.action_import_err);
                            }
                        }
                    }
                }
                ToastUtils.showShort(R.string.action_import_err);
                return false;
            default:
                break;
        }
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (profile == null) {
            finish();
        }

        handler = new Handler();
        appsLoading = new AtomicBoolean();

        setContentView(R.layout.layout_apps);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.proxied_apps);
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = getParentActivityIntent();
            if (shouldUpRecreateTask(intent) || isTaskRoot()) {
                TaskStackBuilder.create(AppManager.this)
                        .addNextIntentWithParentStack(intent)
                        .startActivities();
            } else {
                finish();
            }
        });
        toolbar.inflateMenu(R.menu.app_manager_menu);
        toolbar.setOnMenuItemClickListener(this);

        if (!profile.proxyApps) {
            profile.proxyApps = true;
            ShadowsocksApplication.app.profileManager.updateProfile(profile);
        }

        ((Switch) findViewById(R.id.onSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                profile.proxyApps = checked;
                ShadowsocksApplication.app.profileManager.updateProfile(profile);
                finish();
            }
        });

        bypassSwitch = findViewById(R.id.bypassSwitch);
        bypassSwitch.setChecked(profile.bypass);
        bypassSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            profile.bypass = isChecked;
            ShadowsocksApplication.app.profileManager.updateProfile(profile);
        });
        initProxiedApps();
        loadingView = findViewById(R.id.loading);
        appListView = findViewById(R.id.applistview);
        appListView.setLayoutManager(new LinearLayoutManager(this));
        appListView.setItemAnimator(new DefaultItemAnimator());

        instance = this;

        new Thread(() -> loadAppsAsync()).start();

    }


    private void reloadApps() {
        if (!appsLoading.compareAndSet(true, false)) {
            loadAppsAsync();
        }
    }

    private void loadAppsAsync() {
        if (!appsLoading.compareAndSet(false, true)) {
            return;
        }
        AppsAdapter tempAdapter;
        do {
            appsLoading.set(true);
            tempAdapter = new AppsAdapter();
        } while (!appsLoading.compareAndSet(true, false));

        final AppsAdapter adapter = tempAdapter;
        handler.post(() -> {
            appListView.setAdapter(adapter);
            Utils.crossFade(AppManager.this, loadingView, appListView);
        });
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (toolbar.isOverflowMenuShowing()) {
                return toolbar.hideOverflowMenu();
            } else {
                return toolbar.showOverflowMenu();
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private List<ProxiedApp> getApps(PackageManager pm) {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            ShadowsocksApplication.app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) || !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        synchronized (ProxiedApp.class) {
                            AppManager instance = AppManager.instance;
                            if (instance != null) {
                                instance.reloadApps();
                            }
                        }
                    }
                }
            }, filter);
            receiverRegistered = true;
        }

        synchronized (AppManager.class) {
            if (cachedApps == null) {
                cachedApps = new ArrayList<>();
                List<PackageInfo> packageInfos = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                for (PackageInfo p : packageInfos) {
                    if (p.requestedPermissions != null) {
                        List<String> requestPermissions = Arrays.asList(p.requestedPermissions);
                        if (requestPermissions.contains(Manifest.permission.INTERNET)) {
                            ProxiedApp app = new ProxiedApp(pm.getApplicationLabel(p.applicationInfo).toString(), p.packageName, p.applicationInfo.loadIcon(pm));
                            cachedApps.add(app);
                        }
                    }
                }
            }
        }
        return cachedApps;
    }

    public class ProxiedApp {
        public String name;
        public String packageName;
        public Drawable icon;

        public ProxiedApp(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private class ListEntry {
        public Switch mSwitch;
        public TextView text;
        public ImageView icon;

        public ListEntry(Switch sw, TextView text, ImageView icon) {
            this.mSwitch = sw;
            this.text = text;
            this.icon = icon;
        }
    }

    private class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ImageView icon;
        private Switch check;
        private ProxiedApp item;

        public AppViewHolder(View view) {
            super(view);
            icon = itemView.findViewById(R.id.itemicon);
            check = itemView.findViewById(R.id.itemcheck);

            itemView.setOnClickListener(this);
        }

        private boolean proxied() {
            return proxiedApps.contains(item.packageName);
        }

        public void bind(ProxiedApp app) {
            this.item = app;
            icon.setImageDrawable(app.icon);
            check.setText(app.name);
            check.setChecked(proxied());
        }

        @Override
        public void onClick(View v) {
            if (proxied()) {
                proxiedApps.remove(item.packageName);
                check.setChecked(false);
            } else {
                proxiedApps.add(item.packageName);
                check.setChecked(true);
            }
            if (!appsLoading.get()) {
                profile.individual = Utils.makeString(proxiedApps, "\n");
                ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        }
    }

    private class AppsAdapter extends RecyclerView.Adapter<AppViewHolder> {

        private List<ProxiedApp> apps;

        public AppsAdapter() {
            List<ProxiedApp> apps = getApps(getPackageManager());
            Collections.sort(apps, new Comparator<ProxiedApp>() {
                @Override
                public int compare(ProxiedApp a, ProxiedApp b) {
                    boolean aProxied = proxiedApps.contains(a.packageName);
                    if (aProxied ^ proxiedApps.contains(b.packageName)) {
                        return aProxied ? -1 : 1;
                    } else {
                        boolean result = a.name.compareToIgnoreCase(b.name) < 0;
                        return result ? 1 : -1;
                    }
                }
            });
            this.apps = apps;

        }

        @Override
        public int getItemCount() {
            return apps == null ? 0 : apps.size();
        }

        @Override
        public void onBindViewHolder(AppViewHolder vh, int i) {
            vh.bind(apps.get(i));
        }

        @Override
        public AppViewHolder onCreateViewHolder(ViewGroup vg, int viewType) {
            View view = LayoutInflater.from(vg.getContext()).inflate(R.layout.layout_apps_item, vg, false);
            return new AppViewHolder(view);
        }
    }
}
