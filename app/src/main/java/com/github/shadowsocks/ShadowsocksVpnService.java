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
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.job.AclSyncJob;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ShadowsocksVpnService extends BaseVpnService {

    private static final String TAG = ShadowsocksVpnService.class.getSimpleName();
    private static final int VPN_MTU = 1500;
    private static final String PRIVATE_VLAN = "26.26.26.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";

    private ParcelFileDescriptor conn;
    private ShadowsocksVpnThread vpnThread;
    private ShadowsocksNotification notification;

    private GuardedProcess sslocalProcess;
    private GuardedProcess sstunnelProcess;
    private GuardedProcess pdnsdProcess;
    private GuardedProcess tun2socksProcess;
    private boolean proxychains_enable = false;
    private String host_arg = "";
    private String dns_address = "";
    private int dns_port = 0;
    private String china_dns_address = "";
    private int china_dns_port = 0;

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (SERVICE_INTERFACE.equals(action)) {
            return super.onBind(intent);
        } else if (Constants.Action.SERVICE.equals(action)) {
            return getBinder();
        }
        return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        stopRunner(true);
    }

    @Override
    public void stopRunner(boolean stopService) {
        this.stopRunner(stopService, null);
    }

    @Override
    public void stopRunner(boolean stopService, String msg) {
        if (vpnThread != null) {
            vpnThread.stopThread();
            vpnThread = null;
        }

        if (notification != null) {
            notification.destroy();
        }

        // channge the state
        changeState(Constants.State.STOPPING);

        ShadowsocksApplication.app.track(TAG, "stop");

        // reset VPN
        killProcesses();

        // close connections
        try {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.stopRunner(stopService, msg);
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
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            tun2socksProcess = null;
        }
        if (pdnsdProcess != null) {
            pdnsdProcess.destroy();
            pdnsdProcess = null;
        }
    }

    @Override
    public void startRunner(Profile profile) {
        // ensure the VPNService is prepared
        if (prepare(this) != null) {
            Intent i = new Intent(this, ShadowsocksRunnerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            stopRunner(true);
            return;
        }

        super.startRunner(profile);
    }

    @Override
    public void connect() throws NameNotResolvedException {
        super.connect();

        //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo().dataDir + "/proxychains.conf", true)
        //Os.setenv("PROXYCHAINS_PROTECT_FD_PREFIX", getApplicationInfo().dataDir, true)
        proxychains_enable = new File(getApplicationInfo().dataDir + "/proxychains.conf").exists();

        try {
            List<String> tempList = new ArrayList<>(Arrays.asList(getProfile().getDns().split(",")));
            Collections.shuffle(tempList);
            String dns = tempList.get(0);
            dns_address = dns.split(":")[0];
            dns_port = Integer.parseInt(dns.split(":")[1]);
            tempList.clear();

            tempList = Arrays.asList(getProfile().getChina_dns().split(","));
            Collections.shuffle(tempList);
            String china_dns = tempList.get(0);
            china_dns_address = china_dns.split(":")[0];
            china_dns_port = Integer.parseInt(china_dns.split(":")[1]);
        } catch (Exception e) {
            dns_address = "8.8.8.8";
            dns_port = 53;

            china_dns_address = "223.5.5.5";
            china_dns_port = 53;
        }


        vpnThread = new ShadowsocksVpnThread(this);
        vpnThread.start();

        // reset the context
        killProcesses();

        // Resolve the server address
        host_arg = getProfile().getHost();
        if (!Utils.INSTANCE.isNumeric(getProfile().getHost())) {
            String addr = Utils.INSTANCE.resolve(getProfile().getHost(), getProfile().getIpv6());
            if (TextUtils.isEmpty(addr)) {
                throw new NameNotResolvedException();
            } else {
                getProfile().setHost(addr);
            }
        }

        try {
            handleConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
        changeState(Constants.State.CONNECTED);

        if (!Constants.Route.ALL.equals(getProfile().getRoute())) {
            AclSyncJob.Companion.schedule(getProfile().getRoute());
        }

        notification = new ShadowsocksNotification(this, getProfile().getName());
    }

    /**
     * Called when the activity is first created.
     */
    public void handleConnection() throws Exception {

        int fd = startVpn();
        if (!sendFd(fd)) {
            throw new Exception("sendFd failed");
        }

        startShadowsocksDaemon();

        if (getProfile().getUdpdns()) {
            startShadowsocksUDPDaemon();
        }

        if (!getProfile().getUdpdns()) {
            startDnsDaemon();
            startDnsTunnel();
        }
    }

    public void startShadowsocksUDPDaemon() {
        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                getProfile().getHost(),
                getProfile().getRemotePort(),
                getProfile().getLocalPort(),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getPassword()),
                getProfile().getMethod(),
                600,
                getProfile().getProtocol(),
                getProfile().getObfs(),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getObfs_param()),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getProtocol_param()));
        Utils.INSTANCE.printToFile(new File(getApplicationInfo().dataDir + "/libssr-local.so-udp-vpn.conf"), conf);
        //val old_ld = Os.getenv("LD_PRELOAD")

        //Os.setenv("LD_PRELOAD", getApplicationInfo().dataDir + "/lib/libproxychains4.so", true)
        //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo().dataDir + "/proxychains.conf", true)

        String[] cmd = {getApplicationInfo().nativeLibraryDir + "/libssr-local.so", "-V", "-U",
                "-b", "127.0.0.1",
                "--host", host_arg,
                "-P", getApplicationInfo().dataDir,
                "-c", getApplicationInfo().dataDir + "/libssr-local.so-udp-vpn.conf"};
        LinkedList<String> cmds = new LinkedList<>(Arrays.asList(cmd));
        if (proxychains_enable) {
            cmds.addFirst("LD_PRELOAD=" + getApplicationInfo().dataDir + "/lib/libproxychains4.so");
            cmds.addFirst("PROXYCHAINS_CONF_FILE=" + getApplicationInfo().dataDir + "/proxychains.conf");
            cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo().dataDir);
            cmds.addFirst("env");
        }

        VayLog.INSTANCE.d(TAG, Utils.INSTANCE.makeString(cmds, " "));

        try {
            sstunnelProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Os.setenv("LD_PRELOAD", old_ld, true)
    }

    public void startShadowsocksDaemon() {

        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                getProfile().getHost(),
                getProfile().getRemotePort(),
                getProfile().getLocalPort(),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getPassword()),
                getProfile().getMethod(), 600, getProfile().getProtocol(), getProfile().getObfs(),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getObfs_param()),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getProtocol_param()));

        Utils.INSTANCE.printToFile(new File(getApplicationInfo().dataDir + "/libssr-local.so-vpn.conf"), conf);

        //val old_ld = Os.getenv("LD_PRELOAD")

        //Os.setenv("LD_PRELOAD", getApplicationInfo().dataDir + "/lib/libproxychains4.so", true)
        //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo().dataDir + "/proxychains.conf", true)

        String[] cmd = {getApplicationInfo().nativeLibraryDir + "/libssr-local.so", "-V", "-x",
                "-b", "127.0.0.1",
                "--host", host_arg,
                "-P", getApplicationInfo().dataDir,
                "-c", getApplicationInfo().dataDir + "/libssr-local.so-vpn.conf"};

        LinkedList<String> cmds = new LinkedList<>(Arrays.asList(cmd));

        if (getProfile().getUdpdns()) {
            cmds.add("-u");
        }

        if (!Constants.Route.ALL.equals(getProfile().getRoute())) {
            cmds.add("--acl");
            cmds.add(getApplicationInfo().dataDir + '/' + getProfile().getRoute() + ".acl");
        }

        if (TcpFastOpen.INSTANCE.getSendEnabled()) {
            cmds.add("--fast-open");
        }

        if (proxychains_enable) {
            cmds.addFirst("LD_PRELOAD=" + getApplicationInfo().dataDir + "/lib/libproxychains4.so");
            cmds.addFirst("PROXYCHAINS_CONF_FILE=" + getApplicationInfo().dataDir + "/proxychains.conf");
            cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo().dataDir);
            cmds.addFirst("env");
        }

        VayLog.INSTANCE.d(TAG, Utils.INSTANCE.makeString(cmds, " "));

        try {
            sslocalProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Os.setenv("LD_PRELOAD", old_ld, true)
    }

    public void startDnsTunnel() {
        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                getProfile().getHost(),
                getProfile().getRemotePort(),
                getProfile().getLocalPort() + 63,
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getPassword()),
                getProfile().getMethod(),
                60,
                getProfile().getProtocol(),
                getProfile().getObfs(),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getObfs_param()),
                Constants.ConfigUtils.INSTANCE.EscapedJson(getProfile().getProtocol_param()));
        Utils.INSTANCE.printToFile(new File(getApplicationInfo().dataDir + "/ss-tunnel-vpn.conf"), conf);

        //val old_ld = Os.getenv("LD_PRELOAD")

        //Os.setenv("LD_PRELOAD", getApplicationInfo().dataDir + "/lib/libproxychains4.so", true)
        //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo().dataDir + "/proxychains.conf", true)

        String[] cmd = {getApplicationInfo().nativeLibraryDir + "/libssr-local.so",
                "-V",
                "-u",
                "--host", host_arg,
                "-b", "127.0.0.1",
                "-P", getApplicationInfo().dataDir,
                "-c", getApplicationInfo().dataDir + "/ss-tunnel-vpn.conf"};

        LinkedList<String> cmds = new LinkedList<>(Arrays.asList(cmd));
        cmds.add("-L");
        if (Constants.Route.CHINALIST.equals(getProfile().getRoute())) {
            cmds.add(china_dns_address + ":" + china_dns_port);
        } else {
            cmds.add(dns_address + ":" + dns_port);
        }

        if (proxychains_enable) {
            cmds.addFirst("LD_PRELOAD=" + getApplicationInfo().dataDir + "/lib/libproxychains4.so");
            cmds.addFirst("PROXYCHAINS_CONF_FILE=" + getApplicationInfo().dataDir + "/proxychains.conf");
            cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo().dataDir);
            cmds.addFirst("env");
        }

        VayLog.INSTANCE.d(TAG, Utils.INSTANCE.makeString(cmds, " "));

        try {
            sstunnelProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Os.setenv("LD_PRELOAD", old_ld, true)
    }

    public void startDnsDaemon() {
        String reject = getProfile().getIpv6() ? "224.0.0.0/3" : "224.0.0.0/3, ::/0";
        String protect = "protect = \"" + Companion.getProtectPath() + "\";";

        StringBuilder china_dns_settings = new StringBuilder();

        boolean remote_dns = false;

        if (Constants.Route.ACL.equals(getProfile().getRoute())) {
            //decide acl route
            List<String> total_lines = Utils.INSTANCE.getLinesByFile(new File(getApplicationInfo().dataDir + '/' + getProfile().getRoute() + ".acl"));
            for (String line : total_lines) {
                if ("[remote_dns]".equals(line)) {
                    remote_dns = true;
                }
            }
        }

        String black_list = "";
        if (Constants.Route.BYPASS_CHN.equals(getProfile().getRoute()) || Constants.Route.BYPASS_LAN_CHN.equals(getProfile().getRoute()) || Constants.Route.GFWLIST.equals(getProfile().getRoute())) {
            black_list = getBlackList();
        } else if (Constants.Route.ACL.equals(getProfile().getRoute())) {
            if (remote_dns) {
                black_list = "";
            } else {
                black_list = getBlackList();
            }
        }

        for (String china_dns : getProfile().getChina_dns().split(",")) {
            china_dns_settings.append(String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.REMOTE_SERVER,
                    china_dns.split(":")[0],
                    Integer.parseInt(china_dns.split(":")[1]),
                    black_list,
                    reject));
        }

        String conf = null;
        if (Constants.Route.BYPASS_CHN.equals(getProfile().getRoute()) || Constants.Route.BYPASS_LAN_CHN.equals(getProfile().getRoute()) || Constants.Route.GFWLIST.equals(getProfile().getRoute())) {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_DIRECT,
                    protect,
                    getApplicationInfo().dataDir,
                    "0.0.0.0",
                    getProfile().getLocalPort() + 53,
                    china_dns_settings,
                    getProfile().getLocalPort() + 63,
                    reject);
        } else if (Constants.Route.CHINALIST.equals(getProfile().getRoute())) {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_DIRECT,
                    protect,
                    getApplicationInfo().dataDir,
                    "0.0.0.0",
                    getProfile().getLocalPort() + 53,
                    china_dns_settings,
                    getProfile().getLocalPort() + 63,
                    reject);
        } else if (Constants.Route.ACL.equals(getProfile().getRoute())) {
            if (!remote_dns) {
                conf = String.format(Locale.ENGLISH,
                        Constants.ConfigUtils.PDNSD_DIRECT,
                        protect,
                        getApplicationInfo().dataDir,
                        "0.0.0.0",
                        getProfile().getLocalPort() + 53,
                        china_dns_settings,
                        getProfile().getLocalPort() + 63,
                        reject);
            } else {
                conf = String.format(Locale.ENGLISH,
                        Constants.ConfigUtils.PDNSD_LOCAL,
                        protect,
                        getApplicationInfo().dataDir,
                        "0.0.0.0",
                        getProfile().getLocalPort() + 53,
                        getProfile().getLocalPort() + 63,
                        reject);
            }
        } else {
            conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.PDNSD_LOCAL,
                    protect,
                    getApplicationInfo().dataDir,
                    "0.0.0.0",
                    getProfile().getLocalPort() + 53,
                    getProfile().getLocalPort() + 63,
                    reject);
        }

        Utils.INSTANCE.printToFile(new File(getApplicationInfo().dataDir + "/libpdnsd.so-vpn.conf"), conf);
        String[] cmd = {getApplicationInfo().nativeLibraryDir + "/libpdnsd.so", "-c", getApplicationInfo().dataDir + "/libpdnsd.so-vpn.conf"};
        List<String> cmds = new ArrayList<>(Arrays.asList(cmd));

        VayLog.INSTANCE.d(TAG, Utils.INSTANCE.makeString(cmds, " "));

        try {
            pdnsdProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public int startVpn() {
        Builder builder = new Builder();
        builder.setSession(getProfile().getName())
                .setMtu(VPN_MTU)
                .addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24);

        if (Constants.Route.CHINALIST.equals(getProfile().getRoute())) {
            builder.addDnsServer(china_dns_address);
        } else {
            builder.addDnsServer(dns_address);
        }

        if (getProfile().getIpv6()) {
            builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126);
            builder.addRoute("::", 0);
        }

        if (Utils.INSTANCE.isLollipopOrAbove()) {
            if (getProfile().getProxyApps()) {
                for (String pkg : getProfile().getIndividual().split("\n")) {
                    try {
                        if (!getProfile().getBypass()) {
                            builder.addAllowedApplication(pkg);
                        } else {
                            builder.addDisallowedApplication(pkg);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        VayLog.INSTANCE.e(TAG, "Invalid package name", e);
                    }
                }
            }
        }

        if (Constants.Route.ALL.equals(getProfile().getRoute()) || Constants.Route.BYPASS_CHN.equals(getProfile().getRoute())) {
            builder.addRoute("0.0.0.0", 0);
        } else {
            String[] privateList = getResources().getStringArray(R.array.bypass_private_route);
            for (String cidr : privateList) {
                String[] addr = cidr.split("/");
                builder.addRoute(addr[0], Integer.parseInt(addr[1]));
            }
        }

        if (Constants.Route.CHINALIST.equals(getProfile().getRoute())) {
            builder.addRoute(china_dns_address, 32);
        } else {
            builder.addRoute(dns_address, 32);
        }

        conn = builder.establish();
        if (conn == null) {
            throw new NullConnectionException();
        }

        final int fd = conn.getFd();

        String[] cmd = {getApplicationInfo().nativeLibraryDir + "/libtun2socks.so",
                "--netif-ipaddr", String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"),
                "--netif-netmask", "255.255.255.0",
                "--socks-server-addr", "127.0.0.1:" + getProfile().getLocalPort(),
                "--tunfd", String.valueOf(fd),
                "--tunmtu", String.valueOf(VPN_MTU),
                "--sock-path", getApplicationInfo().dataDir + "/sock_path",
                "--loglevel", "3"};

        List<String> cmds = new ArrayList<>(Arrays.asList(cmd));

        if (getProfile().getIpv6()) {
            cmds.add("--netif-ip6addr");
            cmds.add(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "2"));
        }

        if (getProfile().getUdpdns()) {
            cmds.add("--enable-udprelay");
        } else {
            cmds.add("--dnsgw");
            cmds.add(String.format(Locale.ENGLISH, "%s:%d", String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), getProfile().getLocalPort() + 53));
        }

        VayLog.INSTANCE.d(TAG, Utils.INSTANCE.makeString(cmds, " "));

        try {
            tun2socksProcess = new GuardedProcess(cmds).start(() -> sendFd(fd));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return fd;
    }

    public boolean sendFd(int fd) {
        if (fd != -1) {
            int tries = 1;
            while (tries < 5) {
                try {
                    Thread.sleep(1000 * tries);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (System.INSTANCE.sendfd(fd, getApplicationInfo().dataDir + "/sock_path") != -1) {
                    return true;
                }
                tries += 1;
            }
        }
        return false;
    }
}
