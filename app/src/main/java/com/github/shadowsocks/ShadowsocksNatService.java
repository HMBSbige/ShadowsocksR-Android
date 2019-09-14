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

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.text.TextUtils;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.job.AclSyncJob;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.Nullable;
import eu.chainfire.libsuperuser.Shell;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class ShadowsocksNatService extends BaseService {

    private static final String TAG = ShadowsocksNatService.class.getSimpleName();
    private static final String CMD_IPTABLES_DNAT_ADD_SOCKS = "iptables -t nat -A OUTPUT -p tcp -j DNAT --to-destination 127.0.0.1:8123";

    private ShadowsocksNotification notification;
    private final int myUid = android.os.Process.myUid();

    private GuardedProcess sslocalProcess;
    private GuardedProcess sstunnelProcess;
    private GuardedProcess redsocksProcess;
    private GuardedProcess pdnsdProcess;
    private Shell.Interactive su;
    private boolean proxychains_enable = false;
    private String host_arg = "";
    private String dns_address = "";
    private int dns_port = 0;
    private String china_dns_address = "";
    private int china_dns_port = 0;

    public void startShadowsocksDaemon() {
        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                profile.host,
                profile.remotePort,
                profile.localPort,
                Constants.ConfigUtils.EscapedJson(profile.password),
                profile.method,
                600,
                profile.protocol,
                profile.obfs,
                Constants.ConfigUtils.EscapedJson(profile.obfs_param),
                Constants.ConfigUtils.EscapedJson(profile.protocol_param));

        Utils.printToFile(new File(getApplicationInfo().dataDir + "/ss-local-nat.conf"), conf);

        String[] cmd = {getApplicationInfo().dataDir + "/ss-local", "-x",
                "-b", "127.0.0.1",
                "-t", "600",
                "--host", host_arg,
                "-P", getApplicationInfo().dataDir,
                "-c", getApplicationInfo().dataDir + "/ss-local-nat.conf"};

        LinkedList<String> cmds = new LinkedList<>(Arrays.asList(cmd));
        if (TcpFastOpen.sendEnabled()) {
            cmds.add("--fast-open");
        }

        if (!Constants.Route.ALL.equals(profile.route)) {
            cmds.add("--acl");
            cmds.add(getApplicationInfo().dataDir + '/' + profile.route + ".acl");
        }

        if (proxychains_enable) {
            cmds.addFirst("LD_PRELOAD=" + getApplicationInfo().dataDir + "/lib/libproxychains4.so");
            cmds.addFirst("PROXYCHAINS_CONF_FILE=" + getApplicationInfo().dataDir + "/proxychains.conf");
            cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo().dataDir);
            cmds.addFirst("env");
        }

        VayLog.d(TAG, "startShadowsocksDaemon()  cmds = " + Utils.makeString(cmds, " "));

        try {
            sslocalProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            // Ignored
        }
    }

    public void startTunnel() {
        int localPort = profile.localPort + 63;
        if (profile.udpdns) {
            localPort = profile.localPort + 53;
        }

        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                profile.host,
                profile.remotePort,
                localPort,
                Constants.ConfigUtils.EscapedJson(profile.password),
                profile.method,
                600,
                profile.protocol,
                profile.obfs,
                Constants.ConfigUtils.EscapedJson(profile.obfs_param),
                Constants.ConfigUtils.EscapedJson(profile.protocol_param));

        Utils.printToFile(new File(getApplicationInfo().dataDir + "/ss-tunnel-nat.conf"), conf);
        String[] cmd = {getApplicationInfo().dataDir + "/ss-local",
                "-u",
                "-t", "60",
                "--host", host_arg,
                "-b", "127.0.0.1",
                "-l", String.valueOf(localPort),
                "-P", getApplicationInfo().dataDir,
                "-c", getApplicationInfo().dataDir + "/ss-tunnel-nat.conf"};

        LinkedList<String> cmds = new LinkedList<>(Arrays.asList(cmd));

        cmds.add("-L");

        if (Constants.Route.CHINALIST.equals(profile.route)) {
            cmds.add(china_dns_address + ":" + String.valueOf(china_dns_port));
        } else {
            cmds.add(dns_address + ":" + String.valueOf(dns_port));
        }

        if (proxychains_enable) {
            cmds.addFirst("LD_PRELOAD=" + getApplicationInfo().dataDir + "/lib/libproxychains4.so");
            cmds.addFirst("PROXYCHAINS_CONF_FILE=" + getApplicationInfo().dataDir + "/proxychains.conf");
            cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo().dataDir);
            cmds.addFirst("env");
        }

        VayLog.d(TAG, "startTunnel()  cmds = " + Utils.makeString(cmds, " "));

        try {
            sstunnelProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startDnsDaemon() {
        String reject = profile.ipv6 ? "224.0.0.0/3" : "224.0.0.0/3, ::/0";
        String china_dns_settings = "";
        boolean remote_dns = false;

        if (Constants.Route.ACL.equals(profile.route)) {
            //decide acl route
            List<String> total_lines = Utils.getLinesByFile(new File(getApplicationInfo().dataDir + '/' + profile.route + ".acl"));
            for (String line : total_lines) {
                if ("[remote_dns]".equals(line)) {
                    remote_dns = true;
                }
            }
        }

        String black_list;
        if (Constants.Route.BYPASS_CHN.equals(profile.route) ||
                Constants.Route.BYPASS_LAN_CHN.equals(profile.route) ||
                Constants.Route.GFWLIST.equals(profile.route)) {
            black_list = getBlackList();
        } else if (Constants.Route.ACL.equals(profile.route)) {
            if (remote_dns) {
                black_list = "";
            } else {
                black_list = getBlackList();
            }
        } else {
            black_list = "";
        }

        for (String china_dns : profile.china_dns.split(",")) {
            china_dns_settings += String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.REMOTE_SERVER,
                    china_dns.split(":")[0],
                    Integer.parseInt(china_dns.split(":")[1]),
                    black_list,
                    reject);
        }

        String conf;
        if (Constants.Route.BYPASS_CHN.equals(profile.route) ||
                Constants.Route.BYPASS_LAN_CHN.equals(profile.route) ||
                Constants.Route.GFWLIST.equals(profile.route)) {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_DIRECT,
                    "",
                    getApplicationInfo().dataDir,
                    "127.0.0.1",
                    profile.localPort + 53,
                    china_dns_settings,
                    profile.localPort + 63,
                    reject);
        } else if (Constants.Route.CHINALIST.equals(profile.route)) {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_DIRECT,
                    "",
                    getApplicationInfo().dataDir,
                    "127.0.0.1",
                    profile.localPort + 53,
                    china_dns_settings,
                    profile.localPort + 63,
                    reject);
        } else if (Constants.Route.ACL.equals(profile.route)) {
            if (!remote_dns) {
                conf = String.format(Locale.ENGLISH,
                        Constants.ConfigUtils.PDNSD_DIRECT,
                        "",
                        getApplicationInfo().dataDir,
                        "127.0.0.1",
                        profile.localPort + 53,
                        china_dns_settings,
                        profile.localPort + 63,
                        reject);
            } else {
                conf = String.format(Locale.ENGLISH,
                        Constants.ConfigUtils.PDNSD_LOCAL,
                        "",
                        getApplicationInfo().dataDir,
                        "127.0.0.1",
                        profile.localPort + 53,
                        profile.localPort + 63,
                        reject);
            }
        } else {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_LOCAL,
                    "",
                    getApplicationInfo().dataDir,
                    "127.0.0.1",
                    profile.localPort + 53,
                    profile.localPort + 63,
                    reject);
        }

        Utils.printToFile(new File(getApplicationInfo().dataDir + "/pdnsd-nat.conf"), conf);
        String[] cmd = {
                getApplicationInfo().dataDir + "/pdnsd",
                "-c", getApplicationInfo().dataDir + "/pdnsd-nat.conf"};

        List<String> cmds = Arrays.asList(cmd);
        VayLog.d(TAG, "startDnsDaemon()  cmds = " + Utils.makeString(cmds, " "));

        try {
            pdnsdProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startRedsocksDaemon() {
        String conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.REDSOCKS, profile.localPort);
        String[] cmd = {getApplicationInfo().dataDir + "/redsocks",
                "-c", getApplicationInfo().dataDir + "/redsocks-nat.conf"};
        Utils.printToFile(new File(getApplicationInfo().dataDir + "/redsocks-nat.conf"), conf);

        List<String> cmds = Arrays.asList(cmd);
        VayLog.d(TAG, "startRedsocksDaemon()  cmds = " + Utils.makeString(cmds, " "));
        try {
            redsocksProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when the activity is first created.
     */
    public void handleConnection() {
        startTunnel();
        if (!profile.udpdns) {
            startDnsDaemon();
        }
        startRedsocksDaemon();
        startShadowsocksDaemon();

        try {
            setupIptables();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        VayLog.d(TAG, "onBind");
        if (Constants.Action.SERVICE.equals(intent.getAction())) {
            return binder;
        } else {
            return null;
        }
    }

    public void killProcesses() {
        if (sslocalProcess != null) {
            sslocalProcess.destroy();
            sslocalProcess = null;
        }
        if (sstunnelProcess != null) {
            sstunnelProcess.destroy();
            sstunnelProcess = null;
        }
        if (redsocksProcess != null) {
            redsocksProcess.destroy();
            redsocksProcess = null;
        }
        if (pdnsdProcess != null) {
            pdnsdProcess.destroy();
            pdnsdProcess = null;
        }

        su.addCommand("iptables -t nat -F OUTPUT");
    }

    public void setupIptables() throws Exception {
        List<String> init_sb = new ArrayList<>();
        List<String> http_sb = new ArrayList<>();

        init_sb.add("ulimit -n 4096");
        init_sb.add("iptables -t nat -F OUTPUT");

        String cmd_bypass = "iptables -t nat -A OUTPUT -p tcp -d 0.0.0.0 -j RETURN";
        if (!(InetAddress.getByName(profile.host.toUpperCase()) instanceof Inet6Address)) {
            if (proxychains_enable) {
                RandomAccessFile raf = new RandomAccessFile(getApplicationInfo().dataDir + "/proxychains.conf", "r");
                long len = raf.length();
                String lastLine = "";
                if (len != 0L) {
                    long pos = len - 1;
                    while (pos > 0) {
                        pos -= 1;
                        raf.seek(pos);
                        if (raf.readByte() == '\n' && lastLine.equals("")) {
                            lastLine = raf.readLine();
                        }
                    }
                }
                raf.close();

                String[] str_array = lastLine.split(" ");
                String host = str_array[1];

                if (!Utils.isNumeric(host)) {
                    String a = Utils.resolve(host, true);
                    if (!TextUtils.isEmpty(a)) {
                        host = a;
                    } else {
                        throw new NameNotResolvedException();
                    }
                }

                init_sb.add(cmd_bypass.replace("-p tcp -d 0.0.0.0", "-d " + host));
            } else {
                init_sb.add(cmd_bypass.replace("-p tcp -d 0.0.0.0", "-d " + profile.host));
            }
        }
        init_sb.add(cmd_bypass.replace("-p tcp -d 0.0.0.0", "-d 127.0.0.1"));
        init_sb.add(cmd_bypass.replace("-p tcp -d 0.0.0.0", "-m owner --uid-owner " + myUid));
        init_sb.add(cmd_bypass.replace("-d 0.0.0.0", "--dport 53"));

        init_sb.add("iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"
                + (profile.localPort + 53));

        if (!profile.proxyApps || profile.bypass) {
            http_sb.add(CMD_IPTABLES_DNAT_ADD_SOCKS);
        }

        if (profile.proxyApps) {
            List<ApplicationInfo> infos = getPackageManager().getInstalledApplications(0);
            Map<String, Integer> uidMap = new HashMap<>(infos.size());
            for (ApplicationInfo ai : infos) {
                uidMap.put(ai.packageName, ai.uid);
            }

            for (String pn : profile.individual.split("\n")) {
                int uid = uidMap.get(pn);
                if (!profile.bypass) {
                    http_sb.add(CMD_IPTABLES_DNAT_ADD_SOCKS.replace("-t nat", "-t nat -m owner --uid-owner " + uid));
                } else {
                    init_sb.add(cmd_bypass.replace("-d 0.0.0.0", "-m owner --uid-owner " + uid));
                }
            }
        }
        // init_sb ++ http_sb
        init_sb.addAll(http_sb);
        su.addCommand(init_sb);
    }

    @Override
    public void startRunner(final Profile profile) {
        if (su == null) {
            su = new Shell.Builder().useSU().setWatchdogTimeout(10).open(new Shell.OnShellOpenResultListener() {
                @Override
                public void onOpenResult(boolean success, int reason) {
                    if (success) {
                        ShadowsocksNatService.super.startRunner(profile);
                    } else {
                        if (su != null) {
                            su.close();
                            su = null;
                        }
                        ShadowsocksNatService.super.stopRunner(true, getString(R.string.nat_no_root));
                    }
                }
            });
        }
    }

    @Override
    public void connect() throws NameNotResolvedException, KcpcliParseException {
        super.connect();

        // Clean up
        killProcesses();

        if (new File(getApplicationInfo().dataDir + "/proxychains.conf").exists()) {
            proxychains_enable = true;
            //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo.dataDir + "/proxychains.conf", true)
            //Os.setenv("PROXYCHAINS_PROTECT_FD_PREFIX", getApplicationInfo.dataDir, true)
        } else {
            proxychains_enable = false;
        }

        try {
            List<String> splits = Arrays.asList(profile.dns.split(","));
            Collections.shuffle(splits);
            String dns = splits.get(0);
            dns_address = dns.split(":")[0];
            dns_port = Integer.parseInt(dns.split(":")[1]);

            List<String> china_dns_splits = Arrays.asList(profile.china_dns.split(","));
            Collections.shuffle(china_dns_splits);
            String china_dns = china_dns_splits.get(0);
            china_dns_address = china_dns.split(":")[0];
            china_dns_port = Integer.parseInt(china_dns.split(":")[1]);
        } catch (Exception e) {
            dns_address = "8.8.8.8";
            dns_port = 53;
            china_dns_address = "223.5.5.5";
            china_dns_port = 53;
        }

        host_arg = profile.host;
        if (!Utils.isNumeric(profile.host)) {
            String a = Utils.resolve(profile.host, true);
            if (!TextUtils.isEmpty(a)) {
                profile.host = a;
            } else {
                throw new NameNotResolvedException();
            }
        }

        handleConnection();

        if (!Constants.Route.ALL.equals(profile.route)) {
            AclSyncJob.schedule(profile.route);
        }

        changeState(Constants.State.CONNECTED);

        notification = new ShadowsocksNotification(this, profile.name, true);
    }

    @Override
    public void stopRunner(boolean stopService) {
        stopRunner(stopService, null);
    }

    @Override
    public void stopRunner(boolean stopService, String msg) {
        if (notification != null) {
            notification.destroy();
        }

        // channge the state
        changeState(Constants.State.STOPPING);

        app.track(TAG, "stop");

        // reset NAT
        killProcesses();

        su.close();
        su = null;

        super.stopRunner(stopService, msg);
    }
}
