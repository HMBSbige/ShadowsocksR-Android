package com.github.shadowsocks;
/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.preferences.DropDownPreference;
import com.github.shadowsocks.preferences.NumberPickerPreference;
import com.github.shadowsocks.preferences.PasswordEditTextPreference;
import com.github.shadowsocks.preferences.SummaryEditTextPreference;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.IOUtils;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ShadowsocksSettings extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = ShadowsocksSettings.class.getSimpleName();

    private static final String[] PROXY_PREFS = {
            Constants.Key.group_name, Constants.Key.name, Constants.Key.host,
            Constants.Key.remotePort, Constants.Key.localPort, Constants.Key.password,
            Constants.Key.method, Constants.Key.protocol, Constants.Key.obfs,
            Constants.Key.obfs_param, Constants.Key.dns, Constants.Key.china_dns, Constants.Key.protocol_param};

    private static final String[] FEATURE_PREFS = {
            Constants.Key.route, Constants.Key.proxyApps,
            Constants.Key.udpdns, Constants.Key.ipv6, Constants.Key.tfo};
    public Profile profile;
    private Shadowsocks activity;
    private SwitchPreference isProxyApps;
    private boolean enabled = true;

    /**
     * Helper functions
     */
    public void updateDropDownPreference(Preference pref, String value) {
        ((DropDownPreference) pref).setValue(value);
    }

    public void updatePasswordEditTextPreference(Preference pref, String value) {
        pref.setSummary(value);
        ((PasswordEditTextPreference) pref).setText(value);
    }

    public void updateNumberPickerPreference(Preference pref, int value) {
        ((NumberPickerPreference) pref).setValue(value);
    }

    public void updateSummaryEditTextPreference(Preference pref, String value) {
        pref.setSummary(value);
        ((SummaryEditTextPreference) pref).setText(value);
    }

    public void updateSwitchPreference(Preference pref, boolean value) {
        ((SwitchPreference) pref).setChecked(value);
    }

    public void updatePreference(Preference pref, String name, Profile profile) {
        if (Constants.Key.group_name.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.url_group);
        } else if (Constants.Key.name.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.name);
        } else if (Constants.Key.remotePort.equals(name)) {
            updateNumberPickerPreference(pref, profile.remotePort);
        } else if (Constants.Key.localPort.equals(name)) {
            updateNumberPickerPreference(pref, profile.localPort);
        } else if (Constants.Key.password.equals(name)) {
            updatePasswordEditTextPreference(pref, profile.password);
        } else if (Constants.Key.method.equals(name)) {
            updateDropDownPreference(pref, profile.method);
        } else if (Constants.Key.protocol.equals(name)) {
            updateDropDownPreference(pref, profile.protocol);
        } else if (Constants.Key.protocol_param.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.protocol_param);
        } else if (Constants.Key.obfs.equals(name)) {
            updateDropDownPreference(pref, profile.obfs);
        } else if (Constants.Key.obfs_param.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.obfs_param);
        } else if (Constants.Key.route.equals(name)) {
            updateDropDownPreference(pref, profile.route);
        } else if (Constants.Key.proxyApps.equals(name)) {
            updateSwitchPreference(pref, profile.proxyApps);
        } else if (Constants.Key.udpdns.equals(name)) {
            updateSwitchPreference(pref, profile.udpdns);
        } else if (Constants.Key.dns.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.dns);
        } else if (Constants.Key.china_dns.equals(name)) {
            updateSummaryEditTextPreference(pref, profile.china_dns);
        } else if (Constants.Key.ipv6.equals(name)) {
            updateSwitchPreference(pref, profile.ipv6);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_all);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        activity = (Shadowsocks) getActivity();

        findPreference(Constants.Key.group_name).setOnPreferenceChangeListener((preference, value) -> {
            profile.url_group = (String) value;
            return ShadowsocksApplication.app.profileManager.updateProfile(profile);
        });
        findPreference(Constants.Key.name).setOnPreferenceChangeListener((preference, value) -> {
            profile.name = (String) value;
            return ShadowsocksApplication.app.profileManager.updateProfile(profile);
        });
        findPreference(Constants.Key.host).setOnPreferenceClickListener(preference -> {
            final EditText HostEditText = new EditText(activity);
            HostEditText.setText(profile.host);
            new AlertDialog.Builder(activity)
                    .setTitle(getString(R.string.proxy))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        profile.host = HostEditText.getText().toString();
                        ShadowsocksApplication.app.profileManager.updateProfile(profile);
                    })
                    .setNegativeButton(android.R.string.no, (dialog, which) -> setProfile(profile)).setView(HostEditText).create().show();
            return true;
        });
        findPreference(Constants.Key.remotePort).setOnPreferenceChangeListener((preference, value) -> {
            profile.remotePort = (int) value;
            return ShadowsocksApplication.app.profileManager.updateProfile(profile);
        });
        findPreference(Constants.Key.localPort).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.localPort = (int) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.password).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.password = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.method).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.method = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.protocol).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.protocol = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.protocol_param).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.protocol_param = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.obfs).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.obfs = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });
        findPreference(Constants.Key.obfs_param).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                profile.obfs_param = (String) value;
                return ShadowsocksApplication.app.profileManager.updateProfile(profile);
            }
        });

        findPreference(Constants.Key.route).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, final Object value) {
                if ("self".equals(value)) {
                    final EditText AclUrlEditText = new EditText(activity);
                    AclUrlEditText.setText(getPreferenceManager().getSharedPreferences().getString(Constants.Key.aclurl, ""));
                    new AlertDialog.Builder(activity)
                            .setTitle(getString(R.string.acl_file))
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (TextUtils.isEmpty(AclUrlEditText.getText().toString())) {
                                        setProfile(profile);
                                    } else {
                                        getPreferenceManager().getSharedPreferences().edit().putString(Constants.Key.aclurl, AclUrlEditText.getText().toString()).apply();
                                        downloadAcl(AclUrlEditText.getText().toString());
                                        ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.route, (String) value);
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    setProfile(profile);
                                }
                            })
                            .setView(AclUrlEditText).create().show();
                } else {
                    ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.route, (String) value);
                }
                return true;
            }
        });

        isProxyApps = (SwitchPreference) findPreference(Constants.Key.proxyApps);
        isProxyApps.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(activity, AppManager.class));
                isProxyApps.setChecked(true);
                return false;
            }
        });

        isProxyApps.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                return ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("proxyApps", (boolean) value);
            }
        });

        findPreference(Constants.Key.udpdns).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                return ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("udpdns", (boolean) value);
            }
        });

        findPreference(Constants.Key.dns).

                setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object value) {
                        return ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.dns, (String) value);
                    }
                });

        findPreference(Constants.Key.china_dns).

                setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object value) {
                        return ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.china_dns, (String) value);
                    }
                });

        findPreference(Constants.Key.ipv6).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                return ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("ipv6", (boolean) value);
            }
        });

        SwitchPreference switchPre = (SwitchPreference) findPreference(Constants.Key.isAutoConnect);
        switchPre.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                BootReceiver.setEnabled(activity, (boolean) value);
                return true;
            }
        });

        if (getPreferenceManager().getSharedPreferences().getBoolean(Constants.Key.isAutoConnect, false)) {
            BootReceiver.setEnabled(activity, true);
            getPreferenceManager().getSharedPreferences().edit().remove(Constants.Key.isAutoConnect).apply();
        }

        switchPre.setChecked(BootReceiver.getEnabled(activity));

        SwitchPreference tfo = (SwitchPreference) findPreference(Constants.Key.tfo);
        tfo.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, final Object v) {
                ShadowsocksApplication.app.mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        boolean value = (boolean) v;
                        final String result = TcpFastOpen.enabled(value);
                        if (result != null && !"Success.".equals(result)) {
                            activity.handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Snackbar.make(activity.findViewById(android.R.id.content), result, Snackbar.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
                return true;
            }
        });

        if (!TcpFastOpen.supported()) {
            tfo.setEnabled(false);
            tfo.setSummary(getString(R.string.tcp_fastopen_summary_unsupported, java.lang.System.getProperty("os.version")));
        }

        findPreference("recovery").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ShadowsocksApplication.app.track(TAG, "reset");
                activity.recovery();
                return true;
            }
        });

        findPreference("ignore_battery_optimization").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ShadowsocksApplication.app.track(TAG, "ignore_battery_optimization");
                activity.ignoreBatteryOptimization();
                return true;
            }
        });

        findPreference("aclupdate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ShadowsocksApplication.app.track(TAG, "aclupdate");
                String url = getPreferenceManager().getSharedPreferences().getString(Constants.Key.aclurl, "");
                if ("".equals(url)) {
                    new AlertDialog.Builder(activity)
                            .setTitle(getString(R.string.aclupdate))
                            .setNegativeButton(getString(android.R.string.ok), null)
                            .setMessage(R.string.aclupdate_url_notset)
                            .create()
                            .show();
                } else {
                    downloadAcl(url);
                }
                return true;
            }
        });

        if (!new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + '/' + "self.acl").exists() &&
                !"".equals(getPreferenceManager().getSharedPreferences().getString(Constants.Key.aclurl, ""))) {
            downloadAcl(getPreferenceManager().getSharedPreferences().getString(Constants.Key.aclurl, ""));
        }

        findPreference("about").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ShadowsocksApplication.app.track(TAG, "about");
                WebView web = new WebView(activity);
                web.loadUrl("file:///android_asset/pages/about.html");
                web.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception e) {
                            // Ignore
                        }
                        return true;
                    }
                });

                new AlertDialog.Builder(activity)
                        .setTitle(String.format(Locale.ENGLISH, getString(R.string.about_title), BuildConfig.VERSION_NAME))
                        .setNegativeButton(getString(android.R.string.ok), null)
                        .setView(web)
                        .create()
                        .show();
                return true;
            }
        });

        findPreference("logcat").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ShadowsocksApplication.app.track(TAG, "logcat");
                EditText et_logcat = new EditText(activity);
                try {
                    Process logcat = Runtime.getRuntime().exec("logcat -d");
                    BufferedReader br = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                    String line = "";
                    line = br.readLine();
                    while (line != null) {
                        et_logcat.append(line);
                        et_logcat.append("\n");
                        line = br.readLine();
                    }
                    br.close();
                } catch (Exception e) {
                    // unknown failures, probably shouldn't retry
                    e.printStackTrace();
                }

                new AlertDialog.Builder(activity)
                        .setTitle("Logcat")
                        .setNegativeButton(getString(android.R.string.ok), null)
                        .setView(et_logcat)
                        .create()
                        .show();
                return true;
            }
        });

        findPreference(Constants.Key.frontproxy).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

                View view = View.inflate(activity, R.layout.layout_front_proxy, null);
                Switch sw_frontproxy_enable = view.findViewById(R.id.sw_frontproxy_enable);
                final Spinner sp_frontproxy_type = view.findViewById(R.id.sp_frontproxy_type);
                final EditText et_frontproxy_addr = view.findViewById(R.id.et_frontproxy_addr);
                final EditText et_frontproxy_port = view.findViewById(R.id.et_frontproxy_port);
                final EditText et_frontproxy_username = view.findViewById(R.id.et_frontproxy_username);
                final EditText et_frontproxy_password = view.findViewById(R.id.et_frontproxy_password);

                List<String> stringArray = Arrays.asList(getResources().getStringArray(R.array.frontproxy_type_entry));
                int indexOf = stringArray.indexOf(prefs.getString("frontproxy_type", "socks5"));
                sp_frontproxy_type.setSelection(indexOf);

                if (prefs.getInt("frontproxy_enable", 0) == 1) {
                    sw_frontproxy_enable.setChecked(true);
                }

                et_frontproxy_addr.setText(prefs.getString("frontproxy_addr", ""));
                et_frontproxy_port.setText(prefs.getString("frontproxy_port", ""));
                et_frontproxy_username.setText(prefs.getString("frontproxy_username", ""));
                et_frontproxy_password.setText(prefs.getString("frontproxy_password", ""));

                sw_frontproxy_enable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences.Editor prefs_edit = prefs.edit();
                        if (isChecked) {
                            prefs_edit.putInt("frontproxy_enable", 1);
                            if (!new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf").exists()) {
                                String proxychains_conf = String.format(Locale.ENGLISH,
                                        Constants.ConfigUtils.PROXYCHAINS,
                                        prefs.getString("frontproxy_type", "socks5"),
                                        prefs.getString("frontproxy_addr", ""),
                                        prefs.getString("frontproxy_port", ""),
                                        prefs.getString("frontproxy_username", ""),
                                        prefs.getString("frontproxy_password", ""));
                                Utils.printToFile(new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf"), proxychains_conf, true);
                            }
                        } else {
                            prefs_edit.putInt("frontproxy_enable", 0);
                            if (new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf").exists()) {
                                boolean deleteFlag = new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf").delete();
                                VayLog.d(TAG, "delete proxychains.conf = " + deleteFlag);
                            }
                        }
                        prefs_edit.apply();
                    }
                });

                new AlertDialog.Builder(activity)
                        .setTitle(getString(R.string.frontproxy_set))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor prefs_edit = prefs.edit();
                                prefs_edit.putString("frontproxy_type", sp_frontproxy_type.getSelectedItem().toString());

                                prefs_edit.putString("frontproxy_addr", et_frontproxy_addr.getText().toString());
                                prefs_edit.putString("frontproxy_port", et_frontproxy_port.getText().toString());
                                prefs_edit.putString("frontproxy_username", et_frontproxy_username.getText().toString());
                                prefs_edit.putString("frontproxy_password", et_frontproxy_password.getText().toString());

                                prefs_edit.apply();

                                if (new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf").exists()) {
                                    String proxychains_conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.PROXYCHAINS,
                                            prefs.getString("frontproxy_type", "socks5")
                                            , prefs.getString("frontproxy_addr", "")
                                            , prefs.getString("frontproxy_port", "")
                                            , prefs.getString("frontproxy_username", "")
                                            , prefs.getString("frontproxy_password", ""));
                                    Utils.printToFile(new File(ShadowsocksApplication.app.getApplicationInfo().dataDir + "/proxychains.conf"), proxychains_conf, true);
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .setView(view)
                        .create()
                        .show();
                return true;
            }
        });
    }

    public void downloadAcl(final String url) {
        final ProgressDialog progressDialog = ProgressDialog.show(activity,
                getString(R.string.aclupdate),
                getString(R.string.aclupdate_downloading),
                false, false);

        ShadowsocksApplication.app.mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                HttpURLConnection conn = null;
                InputStream inputStream = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    inputStream = conn.getInputStream();
                    IOUtils.writeString(ShadowsocksApplication.app.getApplicationInfo().dataDir + '/' + "self.acl", IOUtils.readString(inputStream));

                    progressDialog.dismiss();
                    new AlertDialog.Builder(activity, R.style.Theme_Material_Dialog_Alert)
                            .setTitle(getString(R.string.aclupdate))
                            .setNegativeButton(android.R.string.yes, null)
                            .setMessage(getString(R.string.aclupdate_successfully))
                            .create()
                            .show();
                } catch (IOException e) {
                    e.printStackTrace();
                    progressDialog.dismiss();
                    new AlertDialog.Builder(activity, R.style.Theme_Material_Dialog_Alert)
                            .setTitle(getString(R.string.aclupdate))
                            .setNegativeButton(android.R.string.yes, null)
                            .setMessage(getString(R.string.aclupdate_failed))
                            .create()
                            .show();
                } catch (Exception e) {
                    // unknown failures, probably shouldn't retry
                    e.printStackTrace();
                    progressDialog.dismiss();
                    new AlertDialog.Builder(activity, R.style.Theme_Material_Dialog_Alert)
                            .setTitle(getString(R.string.aclupdate))
                            .setNegativeButton(android.R.string.yes, null)
                            .setMessage(getString(R.string.aclupdate_failed))
                            .create()
                            .show();
                } finally {
                    IOUtils.close(inputStream);
                    IOUtils.disconnect(conn);
                }
                Looper.loop();
            }
        });
    }

    public void refreshProfile() {
        Profile profile = ShadowsocksApplication.app.currentProfile();
        if (profile != null) {
            this.profile = profile;
        } else {
            Profile first = ShadowsocksApplication.app.profileManager.getFirstProfile();
            if (first != null) {
                ShadowsocksApplication.app.profileId(first.id);
                this.profile = first;
            } else {
                Profile defaultProfile = ShadowsocksApplication.app.profileManager.createDefault();
                ShadowsocksApplication.app.profileId(defaultProfile.id);
                this.profile = defaultProfile;
            }
        }

        isProxyApps.setChecked(this.profile.proxyApps);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ShadowsocksApplication.app.settings.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        List<String> list = new ArrayList<>();
        list.addAll(Arrays.asList(PROXY_PREFS));
        list.addAll(Arrays.asList(FEATURE_PREFS));

        for (String name : list) {
            Preference pref = findPreference(name);
            if (pref != null) {
                pref.setEnabled(enabled && (!Constants.Key.proxyApps.equals(name) || Utils.isLollipopOrAbove()));
            }
        }
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
        List<String> list = new ArrayList<>();
        list.addAll(Arrays.asList(PROXY_PREFS));
        list.addAll(Arrays.asList(FEATURE_PREFS));

        for (String name : list) {
            updatePreference(findPreference(name), name, profile);
        }
    }
}
