package com.github.shadowsocks;


import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.evernote.android.job.JobManager;
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.database.SSRSubManager;
import com.github.shadowsocks.job.DonaldTrump;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.IOUtils;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.tagmanager.Container;
import com.google.android.gms.tagmanager.ContainerHolder;
import com.google.android.gms.tagmanager.TagManager;
import com.j256.ormlite.logger.LocalLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import eu.chainfire.libsuperuser.Shell;

public class ShadowsocksApplication extends Application {
    public static ShadowsocksApplication app;

    private static final String TAG = ShadowsocksApplication.class.getSimpleName();
    public static final String SIG_FUNC = "getSignature";

    private String[] EXECUTABLES = {
            Constants.Executable.PDNSD,
            Constants.Executable.REDSOCKS,
            Constants.Executable.SS_TUNNEL,
            Constants.Executable.SS_LOCAL,
            Constants.Executable.TUN2SOCKS,
            Constants.Executable.KCPTUN};

    /**
     * The ones in Locale doesn't have script included
     */
    private static final Locale SIMPLIFIED_CHINESE;
    private static final Locale TRADITIONAL_CHINESE;

    static {
        if (Build.VERSION.SDK_INT >= 21) {
            SIMPLIFIED_CHINESE = Locale.forLanguageTag("zh-Hans-CN");
            TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-Hant-TW");
        } else {
            SIMPLIFIED_CHINESE = Locale.SIMPLIFIED_CHINESE;
            TRADITIONAL_CHINESE = Locale.TRADITIONAL_CHINESE;
        }
    }

    public ContainerHolder containerHolder;

    private Tracker tracker;
    public SharedPreferences settings;
    public SharedPreferences.Editor editor;

    public ProfileManager profileManager;
    public SSRSubManager ssrsubManager;
    public Resources resources;

    public boolean isNatEnabled() {
        return false;
    }


    public boolean isVpnEnabled() {
        return !isNatEnabled();
    }

    public ScheduledExecutorService mThreadPool;

    /**
     * /// xhao: init variable
     */
    private void initVariable() {
        tracker = GoogleAnalytics.getInstance(this).newTracker(R.xml.tracker);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        editor = settings.edit();

        profileManager = new ProfileManager(new DBHelper(this));
        ssrsubManager = new SSRSubManager(new DBHelper(this));
        resources = getResources();

        mThreadPool = new ScheduledThreadPoolExecutor(10, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("shadowsocksr-thread");
                return thread;
            }
        });
    }

    /**
     * send event
     */
    public void track(String category, String action) {
        Map<String, String> builders = new HitBuilders.EventBuilder()
                .setAction(action)
                .setCategory(category)
                .setLabel(BuildConfig.VERSION_NAME)
                .build();
        tracker.send(builders);
    }

    /**
     * send event
     */
    public void track(Throwable t) {
        Map<String, String> builders = new HitBuilders.ExceptionBuilder()
                .setDescription(new StandardExceptionParser(this, null).getDescription(Thread.currentThread().getName(), t))
                .setFatal(false)
                .build();
        tracker.send(builders);
    }

    /**
     * get profile id
     *
     * @return no save return -1
     */
    public int profileId() {
        return settings.getInt(Constants.Key.id, -1);
    }

    /**
     * save profile id
     *
     * @param i profile id
     */
    public void profileId(int i) {
        editor.putInt(Constants.Key.id, i).apply();
    }

    /**
     * current profile
     */
    public Profile currentProfile() {
        return profileManager.getProfile(profileId());
    }

    /**
     * switch profile
     *
     * @param id profile id
     */
    public Profile switchProfile(int id) {
        profileId(id);

        Profile profile = profileManager.getProfile(id);
        if (profile != null) {
            return profile;
        } else {
            return profileManager.createProfile();
        }
    }

    @SuppressLint("NewApi")
    private Locale checkChineseLocale(Locale locale) {
        if ("zh".equals(locale.getLanguage())) {
            String country = locale.getCountry();
            if ("CN".equals(country) || "TW".equals(country)) {
                return null;
            } else {
                String script = locale.getScript();
                if ("Hans".equals(script)) {
                    return SIMPLIFIED_CHINESE;
                } else if ("Hant".equals(script)) {
                    return TRADITIONAL_CHINESE;
                } else {
                    VayLog.w(TAG, String.format("Unknown zh locale script: %s. Falling back to trying countries...", script));
                    if ("SG".equals(country)) {
                        return SIMPLIFIED_CHINESE;
                    } else if ("HK".equals(country) || "MO".equals(country)) {
                        return TRADITIONAL_CHINESE;
                    } else {
                        VayLog.w(TAG, String.format("Unknown zh locale: %s. Falling back to zh-Hans-CN...", locale.toLanguageTag()));
                        return SIMPLIFIED_CHINESE;
                    }
                }
            }
        } else {
            return null;
        }
    }

    /**
     * check chinese locale
     */
    private void checkChineseLocale(Configuration config) {
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList localeList = config.getLocales();
            Locale[] newList = new Locale[localeList.size()];
            boolean changed = false;
            for (int i = 0; i < localeList.size(); i++) {
                Locale locale = localeList.get(i);
                Locale newLocale = checkChineseLocale(locale);
                if (newLocale == null) {
                    newList[i] = locale;
                } else {
                    newList[i] = newLocale;
                    changed = true;
                }
            }
            if (changed) {
                Configuration newConfig = new Configuration(config);
                newConfig.setLocales(new LocaleList(newList));
                Resources res = getResources();
                res.updateConfiguration(newConfig, res.getDisplayMetrics());
            }
        } else {
            Locale newLocale = checkChineseLocale(config.locale);
            if (newLocale != null) {
                Configuration newConfig = new Configuration(config);
                newConfig.locale = newLocale;
                Resources res = getResources();
                res.updateConfiguration(newConfig, res.getDisplayMetrics());
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        checkChineseLocale(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        // init toast utils
        ToastUtils.init(getApplicationContext());
        initVariable();

        if (!BuildConfig.DEBUG) {
            java.lang.System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        }
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        checkChineseLocale(getResources().getConfiguration());
        TagManager tm = TagManager.getInstance(this);
        PendingResult<ContainerHolder> pending = tm.loadContainerPreferNonDefault("GTM-NT8WS8", R.raw.gtm_default_container);
        ResultCallback<ContainerHolder> callback = new ResultCallback<ContainerHolder>() {
            @Override
            public void onResult(@NonNull ContainerHolder holder) {
                if (!holder.getStatus().isSuccess()) {
                    return;
                }
                containerHolder = holder;
                Container container = holder.getContainer();
                container.registerFunctionCallMacroCallback(SIG_FUNC, new Container.FunctionCallMacroCallback() {
                    @Override
                    public Object getValue(String functionName, Map<String, Object> parameters) {
                        if (SIG_FUNC.equals(functionName)) {
                            return Utils.getSignature(getApplicationContext());
                        } else {
                            return null;
                        }
                    }
                });
            }
        };
        pending.setResultCallback(callback, 2, TimeUnit.SECONDS);
        JobManager.create(this).addJobCreator(new DonaldTrump());

        if (settings.getBoolean(Constants.Key.tfo, false) && TcpFastOpen.supported()) {
            mThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    TcpFastOpen.enabled(settings.getBoolean(Constants.Key.tfo, false));
                }
            });
        }
    }

    /**
     * refresh container holder
     */
    public void refreshContainerHolder() {
        if (containerHolder != null) {
            containerHolder.refresh();
        }
    }

    /**
     * copy assets
     *
     * @param path assets path
     */
    private void copyAssets(String path) {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list(path);
        } catch (Exception e) {
            VayLog.e(TAG, e.getMessage());
            app.track(e);
        }

        if (files != null) {
            for (String file : files) {
                InputStream in = null;
                FileOutputStream fos = null;
                try {
                    if (!TextUtils.isEmpty(path)) {
                        in = assetManager.open(path + File.separator + file);
                    } else {
                        in = assetManager.open(file);
                    }
                    fos = new FileOutputStream(getApplicationInfo().dataDir + '/' + file);
                    IOUtils.copy(in, fos);
                } catch (IOException e) {
                    VayLog.e(TAG, "copyAssets", e);
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                    } catch (Exception e) {
                        VayLog.e(TAG, "copyAssets", e);
                    }

                    try {
                        if (fos != null) {
                            fos.close();
                        }
                    } catch (Exception e) {
                        VayLog.e(TAG, "copyAssets", e);
                    }
                }
            }
        }
    }

    /**
     * arash recovery
     */
    public void crashRecovery() {
        ArrayList<String> cmd = new ArrayList<>();

        String[] paramsArray = {"libssr-local.so", "ss-tunnel", "libpdnsd.so", "libredsocks.so", "libtun2socks.so", "proxychains"};
        for (String task : paramsArray) {
            cmd.add(String.format(Locale.ENGLISH, "killall %s", task));
            cmd.add(String.format(Locale.ENGLISH, "rm -f %1$s/%2$s-nat.conf %1$s/%2$s-vpn.conf", getApplicationInfo().dataDir, task));
        }

        // convert to cmd array
        String[] cmds = convertListToStringArray(cmd);
        if (app.isNatEnabled()) {
            cmd.add("iptables -t nat -F OUTPUT");
            cmd.add("echo done");
            List<String> result = Shell.SU.run(cmds);
            if (result != null && !result.isEmpty()) {
                // fallback to SH
                return;
            }
        }

        Shell.SH.run(cmds);
    }

    /**
     * convert list to string array
     *
     * @param list list
     * @return convert failed return {}
     */
    private String[] convertListToStringArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return new String[]{};
        }

        // start convert
        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    /**
     * copy assets
     */
    public void copyAssets() {
        // ensure executables are killed before writing to them
        crashRecovery();
        copyAssets(System.getABI());
        copyAssets("acl");

        // exec cmds
        String[] cmds = new String[EXECUTABLES.length];
        for (int i = 0; i < cmds.length; i++) {
            cmds[i] = "chmod 755 " + getApplicationInfo().nativeLibraryDir + File.separator + EXECUTABLES[i];
        }
        Shell.SH.run(cmds);

        // save current version code
        editor.putInt(Constants.Key.currentVersionCode, BuildConfig.VERSION_CODE).apply();
    }

    /**
     * update assets
     */
    public void updateAssets() {
        if (settings.getInt(Constants.Key.currentVersionCode, -1) != BuildConfig.VERSION_CODE) {
            copyAssets();
        }
    }
}
