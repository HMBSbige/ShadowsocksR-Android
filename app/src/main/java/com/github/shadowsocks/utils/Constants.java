package com.github.shadowsocks.utils;
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

public class Constants {
    public static class Executable {
        public static final String REDSOCKS = "redsocks";
        public static final String PDNSD = "pdnsd";
        public static final String SS_LOCAL = "ss-local";
        public static final String SS_TUNNEL = "ss-tunnel";
        public static final String TUN2SOCKS = "tun2socks";
        public static final String KCPTUN = "kcptun";
    }

    public static class ConfigUtils {

        public static String EscapedJson(String OriginString) {
            return OriginString.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"");
        }

        public static final String SHADOWSOCKS = "{\"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"timeout\": %d, \"protocol\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"protocol_param\": \"%s\"}";
        public static final String REDSOCKS = "base {\n" +
                " log_debug = off;\n" +
                " log_info = off;\n" +
                " log = stderr;\n" +
                " daemon = off;\n" +
                " redirector = iptables;\n" +
                "}\n" +
                "redsocks {\n" +
                " local_ip = 127.0.0.1;\n" +
                " local_port = 8123;\n" +
                " ip = 127.0.0.1;\n" +
                " port = %d;\n" +
                " type = socks5;\n" +
                "}\n";

        public static final String PROXYCHAINS = "strict_chain\n" +
                "localnet 127.0.0.0/255.0.0.0\n" +
                "[ProxyList]\n" +
                "%s %s %s %s %s";

        public static final String PDNSD_LOCAL =
                "global {" +
                        "perm_cache = 2048;" +
                        "%s" +
                        "cache_dir = \"%s\";" +
                        "server_ip = %s;" +
                        "server_port = %d;" +
                        "query_method = tcp_only;" +
                        "min_ttl = 15m;" +
                        "max_ttl = 1w;" +
                        "timeout = 10;" +
                        "daemon = off;" +
                        "}" +
                        "" +
                        "server {" +
                        "label = \"local\";" +
                        "ip = 127.0.0.1;" +
                        "port = %d;" +
                        "reject = %s;" +
                        "reject_policy = negate;" +
                        "reject_recursively = on;" +
                        "}" +
                        "" +
                        "rr {" +
                        "name=localhost;" +
                        "reverse=on;" +
                        "a=127.0.0.1;" +
                        "owner=localhost;" +
                        "soa=localhost,root.localhost,42,86400,900,86400,86400;" +
                        "}";

        public static final String PDNSD_DIRECT =
                "global {" +
                        "perm_cache = 2048;" +
                        "%s" +
                        "cache_dir = \"%s\";" +
                        "server_ip = %s;" +
                        "server_port = %d;" +
                        "query_method = udp_only;" +
                        "min_ttl = 15m;" +
                        "max_ttl = 1w;" +
                        "timeout = 10;" +
                        "daemon = off;" +
                        "par_queries = 4;" +
                        "}" +
                        "" +
                        "%s" +
                        "" +
                        "server {" +
                        "label = \"local-server\";" +
                        "ip = 127.0.0.1;" +
                        "query_method = tcp_only;" +
                        "port = %d;" +
                        "reject = %s;" +
                        "reject_policy = negate;" +
                        "reject_recursively = on;" +
                        "}" +
                        "" +
                        "rr {" +
                        "name=localhost;" +
                        "reverse=on;" +
                        "a=127.0.0.1;" +
                        "owner=localhost;" +
                        "soa=localhost,root.localhost,42,86400,900,86400,86400;" +
                        "}";

        public static final String REMOTE_SERVER =
                "server {" +
                        "label = \"remote-servers\";" +
                        "ip = %s;" +
                        "port = %d;" +
                        "timeout = 3;" +
                        "query_method = udp_only;" +
                        "%s" +
                        "policy = included;" +
                        "reject = %s;" +
                        "reject_policy = fail;" +
                        "reject_recursively = on;" +
                        "}";
    }

    public static class Key {
        public static final String id = "profileId";
        public static final String name = "profileName";

        public static final String individual = "Proxyed";

        public static final String isNAT = "isNAT";
        public static final String route = "route";
        public static final String aclurl = "aclurl";

        public static final String isAutoConnect = "isAutoConnect";

        public static final String proxyApps = "isProxyApps";
        public static final String bypass = "isBypassApps";
        public static final String udpdns = "isUdpDns";
        public static final String auth = "isAuth";
        public static final String ipv6 = "isIpv6";

        public static final String host = "proxy";
        public static final String password = "sitekey";
        public static final String method = "encMethod";
        public static final String remotePort = "remotePortNum";
        public static final String localPort = "localPortNum";

        public static final String profileTip = "profileTip";

        public static final String obfs = "obfs";
        public static final String obfs_param = "obfs_param";
        public static final String protocol = "protocol";
        public static final String protocol_param = "protocol_param";
        public static final String dns = "dns";
        public static final String china_dns = "china_dns";

        public static final String tfo = "tcp_fastopen";
        public static final String currentVersionCode = "currentVersionCode";
        public static final String logcat = "logcat";
        public static final String frontproxy = "frontproxy";
        public static final String ssrsub_autoupdate = "ssrsub_autoupdate";
        public static final String group_name = "groupName";
    }

    public static class State {
        public static final int CONNECTING = 1;
        public static final int CONNECTED = 2;
        public static final int STOPPING = 3;
        public static final int STOPPED = 4;

        public static boolean isAvailable(int state) {
            return state != CONNECTED && state != CONNECTING;
        }
    }

    public static class Action {
        public static final String SERVICE = "com.github.shadowsocks.SERVICE";
        public static final String CLOSE = "com.github.shadowsocks.CLOSE";
        public static final String QUICK_SWITCH = "com.github.shadowsocks.QUICK_SWITCH";
        public static final String SCAN = "com.github.shadowsocks.intent.action.SCAN";
        public static final String SORT = "com.github.shadowsocks.intent.action.SORT";
    }

    public static class Route {
        public static final String ALL = "all";
        public static final String BYPASS_LAN = "bypass-lan";
        public static final String BYPASS_CHN = "bypass-china";
        public static final String BYPASS_LAN_CHN = "bypass-lan-china";
        public static final String GFWLIST = "gfwlist";
        public static final String CHINALIST = "china-list";
        public static final String ACL = "self";
    }
}