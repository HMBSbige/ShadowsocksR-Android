package com.bige0.shadowsocksr.utils

object Constants
{
	const val DefaultHostName = "198.199.101.152"

	object Executable
	{
		const val PDNSD = "libpdnsd.so"
		const val PROXYCHAINS4 = "libproxychains4.so"
		const val SS_LOCAL = "libssr-local.so"
		const val TUN2SOCKS = "libtun2socks.so"
	}

	object ConfigUtils
	{
		const val SHADOWSOCKS = "{\"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"timeout\": %d, \"protocol\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"protocol_param\": \"%s\"}"
		const val PROXYCHAINS = "strict_chain\n" + "localnet 127.0.0.0/255.0.0.0\n" + "[ProxyList]\n" + "%s %s %s %s %s"
		const val PDNSD_LOCAL = "global {" + "perm_cache = 2048;" + "%s" + "cache_dir = \"%s\";" + "server_ip = %s;" + "server_port = %d;" + "query_method = tcp_only;" + "min_ttl = 15m;" + "max_ttl = 1w;" + "timeout = 10;" + "daemon = off;" + "}" + "" + "server {" + "label = \"local\";" + "ip = 127.0.0.1;" + "port = %d;" + "reject = %s;" + "reject_policy = negate;" + "reject_recursively = on;" + "}" + "" + "rr {" + "name=localhost;" + "reverse=on;" + "a=127.0.0.1;" + "owner=localhost;" + "soa=localhost,root.localhost,42,86400,900,86400,86400;" + "}"
		const val PDNSD_DIRECT = "global {" + "perm_cache = 2048;" + "%s" + "cache_dir = \"%s\";" + "server_ip = %s;" + "server_port = %d;" + "query_method = udp_only;" + "min_ttl = 15m;" + "max_ttl = 1w;" + "timeout = 10;" + "daemon = off;" + "par_queries = 4;" + "}" + "" + "%s" + "" + "server {" + "label = \"local-server\";" + "ip = 127.0.0.1;" + "query_method = tcp_only;" + "port = %d;" + "reject = %s;" + "reject_policy = negate;" + "reject_recursively = on;" + "}" + "" + "rr {" + "name=localhost;" + "reverse=on;" + "a=127.0.0.1;" + "owner=localhost;" + "soa=localhost,root.localhost,42,86400,900,86400,86400;" + "}"
		const val REMOTE_SERVER = "server {" + "label = \"remote-servers\";" + "ip = %s;" + "port = %d;" + "timeout = 3;" + "query_method = udp_only;" + "%s" + "policy = included;" + "reject = %s;" + "reject_policy = fail;" + "reject_recursively = on;" + "}"

		fun escapedJson(OriginString: String): String
		{
			return OriginString.replace("\\\\", "\\\\\\\\")
				.replace("\"", "\\\\\"")
		}
	}

	object Key
	{
		const val id = "profileId"
		const val name = "profileName"

		const val individual = "Proxyed"

		const val route = "route"
		const val aclurl = "aclurl"

		const val isAutoConnect = "isAutoConnect"

		const val proxyApps = "isProxyApps"
		const val udpdns = "isUdpDns"
		const val ipv6 = "isIpv6"

		const val host = "proxy"
		const val password = "sitekey"
		const val method = "encMethod"
		const val remotePort = "remotePortNum"
		const val localPort = "localPortNum"

		const val profileTip = "profileTip"

		const val obfs = "obfs"
		const val obfs_param = "obfs_param"
		const val protocol = "protocol"
		const val protocol_param = "protocol_param"
		const val dns = "dns"
		const val china_dns = "china_dns"

		const val currentVersionCode = "currentVersionCode"
		const val frontproxy = "frontproxy"
		const val ssrsub_autoupdate = "ssrsub_autoupdate"
		const val group_name = "groupName"
	}

	object State
	{
		const val CONNECTING = 1
		const val CONNECTED = 2
		const val STOPPING = 3
		const val STOPPED = 4
	}

	object Action
	{
		const val SERVICE = "com.bige0.shadowsocksr.SERVICE"
		const val CLOSE = "com.bige0.shadowsocksr.CLOSE"
		const val QUICK_SWITCH = "com.bige0.shadowsocksr.QUICK_SWITCH"
		const val SCAN = "com.bige0.shadowsocksr.intent.action.SCAN"
		const val SORT = "com.bige0.shadowsocksr.intent.action.SORT"
	}

	object Route
	{
		const val ALL = "all"
		const val BYPASS_LAN = "bypass-lan"
		const val BYPASS_CHN = "bypass-china"
		const val BYPASS_LAN_CHN = "bypass-lan-china"
		const val GFWLIST = "gfwlist"
		const val CHINALIST = "china-list"
		const val ACL = "self"
	}
}