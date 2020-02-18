package com.bige0.shadowsocksr

import android.content.*
import android.content.pm.*
import android.net.*
import android.os.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.job.*
import com.bige0.shadowsocksr.utils.*
import java.io.*
import java.util.*

class ShadowsocksVpnService : com.bige0.shadowsocksr.BaseVpnService()
{
	companion object
	{
		private const val TAG = "ShadowsocksVpnService"
		private const val VPN_MTU = 1500
		private const val PRIVATE_VLAN = "172.19.0.%s"
		private const val PRIVATE_VLAN6 = "fdfe:dcba:9876::%s"
	}

	private var conn: ParcelFileDescriptor? = null
	private var vpnThread: ShadowsocksVpnThread? = null
	private var notification: ShadowsocksNotification? = null

	private var sslocalProcess: GuardedProcess? = null
	private var sstunnelProcess: GuardedProcess? = null
	private var pdnsdProcess: GuardedProcess? = null
	private var tun2socksProcess: GuardedProcess? = null
	private var proxychainsEnable = false
	private var hostArg = ""
	private var dnsAddress = ""
	private var dnsPort = 0
	private var chinaDnsAddress = ""
	private var chinaDnsPort = 0

	override fun onBind(intent: Intent): IBinder?
	{
		val action = intent.action
		if (SERVICE_INTERFACE == action)
		{
			return super.onBind(intent)
		}
		else if (Constants.Action.SERVICE == action)
		{
			return binder
		}
		return super.onBind(intent)
	}

	override fun onRevoke()
	{
		stopRunner(true)
	}

	override fun stopRunner(stopService: Boolean)
	{
		this.stopRunner(stopService, null)
	}

	override fun stopRunner(stopService: Boolean, msg: String?)
	{
		if (vpnThread != null)
		{
			vpnThread!!.stopThread()
			vpnThread = null
		}

		if (notification != null)
		{
			notification!!.destroy()
		}

		// channge the state
		changeState(Constants.State.STOPPING)

		ShadowsocksApplication.app.track(TAG, "stop")

		// reset VPN
		killProcesses()

		// close connections
		try
		{
			if (conn != null)
			{
				conn!!.close()
				conn = null
			}
		}
		catch (e: IOException)
		{
			e.printStackTrace()
		}

		super.stopRunner(stopService, msg)
	}

	private fun killProcesses()
	{
		if (sslocalProcess != null)
		{
			sslocalProcess!!.destroy()
			sslocalProcess = null
		}
		if (sstunnelProcess != null)
		{
			sstunnelProcess!!.destroy()
			sstunnelProcess = null
		}
		if (tun2socksProcess != null)
		{
			tun2socksProcess!!.destroy()
			tun2socksProcess = null
		}
		if (pdnsdProcess != null)
		{
			pdnsdProcess!!.destroy()
			pdnsdProcess = null
		}
	}

	override fun startRunner(profile: Profile)
	{
		// ensure the VPNService is prepared
		if (prepare(this) != null)
		{
			val i = Intent(this, ShadowsocksRunnerActivity::class.java)
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			startActivity(i)
			stopRunner(true)
			return
		}

		super.startRunner(profile)
	}

	override fun connect()
	{
		super.connect()

		proxychainsEnable = File("${applicationInfo.dataDir}/proxychains.conf").exists()

		try
		{
			var tempList: MutableList<String> = profile!!.dns.split(",")
				.toMutableList()
			tempList.shuffle()
			val dns = tempList[0]
			dnsAddress = dns.split(":")[0]
			dnsPort = Integer.parseInt(dns.split(":")[1])
			tempList.clear()

			tempList = profile!!.china_dns.split(",")
				.toMutableList()
			tempList.shuffle()
			val chinaDns = tempList[0]
			chinaDnsAddress = chinaDns.split(":")[0]
			chinaDnsPort = Integer.parseInt(chinaDns.split(":")[1])
		}
		catch (e: Exception)
		{
			dnsAddress = "8.8.8.8"
			dnsPort = 53

			chinaDnsAddress = "223.5.5.5"
			chinaDnsPort = 53
		}

		vpnThread = ShadowsocksVpnThread(this)
		vpnThread!!.start()

		// reset the context
		killProcesses()

		// Resolve the server address
		hostArg = profile!!.host
		if (!Utils.isNumeric(profile!!.host))
		{
			val addr = Utils.resolve(profile!!.host, profile!!.ipv6)
			if (addr.isNullOrEmpty())
			{
				throw NameNotResolvedException()
			}
			profile!!.host = addr
		}

		try
		{
			handleConnection()
		}
		catch (e: Exception)
		{
			e.printStackTrace()
		}

		changeState(Constants.State.CONNECTED)

		if (Constants.Route.ALL != profile!!.route)
		{
			AclSyncJob.schedule(profile!!.route)
		}

		notification = ShadowsocksNotification(this, profile!!.name)
	}

	/**
	 * Called when the activity is first created.
	 */
	private fun handleConnection()
	{
		if (!sendFd(startVpn()))
		{
			throw Exception("sendFd failed")
		}

		startShadowsocksDaemon()

		if (profile!!.udpdns)
		{
			startShadowsocksUDPDaemon()
		}

		if (!profile!!.udpdns)
		{
			startDnsDaemon()
			startDnsTunnel()
		}
	}

	private fun startShadowsocksUDPDaemon()
	{
		val conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.SHADOWSOCKS, profile!!.host, profile!!.remotePort, profile!!.localPort, Constants.ConfigUtils.escapedJson(profile!!.password), profile!!.method, 600, profile!!.protocol, profile!!.obfs, Constants.ConfigUtils.escapedJson(profile!!.obfs_param), Constants.ConfigUtils.escapedJson(profile!!.protocol_param))
		Utils.printToFile(File(applicationInfo.dataDir + "/libssr-local.so-udp-vpn.conf"), conf)

		val cmd = arrayOf("${applicationInfo.nativeLibraryDir}/${Constants.Executable.SS_LOCAL}", "-V", "-U", "-b", "127.0.0.1", "--host", hostArg, "-P", applicationInfo.dataDir, "-c", applicationInfo.dataDir + "/libssr-local.so-udp-vpn.conf")
		val cmds = LinkedList(cmd.toList())
		if (proxychainsEnable)
		{
			cmds.addFirst("LD_PRELOAD=" + applicationInfo.dataDir + "/lib/${Constants.Executable.PROXYCHAINS4}")
			cmds.addFirst("PROXYCHAINS_CONF_FILE=" + applicationInfo.dataDir + "/proxychains.conf")
			cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + applicationInfo.dataDir)
			cmds.addFirst("env")
		}

		VayLog.d(TAG, Utils.makeString(cmds, " "))

		try
		{
			sstunnelProcess = GuardedProcess(cmds)
				.start()
		}
		catch (e: InterruptedException)
		{
			e.printStackTrace()
		}
	}

	private fun startShadowsocksDaemon()
	{

		val conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.SHADOWSOCKS, profile!!.host, profile!!.remotePort, profile!!.localPort, Constants.ConfigUtils.escapedJson(profile!!.password), profile!!.method, 600, profile!!.protocol, profile!!.obfs, Constants.ConfigUtils.escapedJson(profile!!.obfs_param), Constants.ConfigUtils.escapedJson(profile!!.protocol_param))

		Utils.printToFile(File(applicationInfo.dataDir + "/libssr-local.so-vpn.conf"), conf)

		val cmd = arrayOf(applicationInfo.nativeLibraryDir + "/${Constants.Executable.SS_LOCAL}", "-V", "-x", "-b", "127.0.0.1", "--host", hostArg, "-P", applicationInfo.dataDir, "-c", applicationInfo.dataDir + "/libssr-local.so-vpn.conf")

		val cmds = LinkedList(cmd.toList())

		if (profile!!.udpdns)
		{
			cmds.add("-u")
		}

		if (Constants.Route.ALL != profile!!.route)
		{
			cmds.add("--acl")
			cmds.add(applicationInfo.dataDir + '/'.toString() + profile!!.route + ".acl")
		}

		if (proxychainsEnable)
		{
			cmds.addFirst("LD_PRELOAD=" + applicationInfo.dataDir + "/lib/${Constants.Executable.PROXYCHAINS4}")
			cmds.addFirst("PROXYCHAINS_CONF_FILE=" + applicationInfo.dataDir + "/proxychains.conf")
			cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + applicationInfo.dataDir)
			cmds.addFirst("env")
		}

		VayLog.d(TAG, Utils.makeString(cmds, " "))

		try
		{
			sslocalProcess = GuardedProcess(cmds)
				.start()
		}
		catch (e: InterruptedException)
		{
			e.printStackTrace()
		}
	}

	private fun startDnsTunnel()
	{
		val conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.SHADOWSOCKS, profile!!.host, profile!!.remotePort, profile!!.localPort + 63, Constants.ConfigUtils.escapedJson(profile!!.password), profile!!.method, 60, profile!!.protocol, profile!!.obfs, Constants.ConfigUtils.escapedJson(profile!!.obfs_param), Constants.ConfigUtils.escapedJson(profile!!.protocol_param))
		Utils.printToFile(File(applicationInfo.dataDir + "/ss-tunnel-vpn.conf"), conf)

		val cmd = arrayOf(applicationInfo.nativeLibraryDir + "/${Constants.Executable.SS_LOCAL}", "-V", "-u", "--host", hostArg, "-b", "127.0.0.1", "-P", applicationInfo.dataDir, "-c", applicationInfo.dataDir + "/ss-tunnel-vpn.conf")

		val cmds = LinkedList(cmd.toList())
		cmds.add("-L")
		if (Constants.Route.CHINALIST == profile!!.route)
		{
			cmds.add("$chinaDnsAddress:$chinaDnsPort")
		}
		else
		{
			cmds.add("$dnsAddress:$dnsPort")
		}

		if (proxychainsEnable)
		{
			cmds.addFirst("LD_PRELOAD=" + applicationInfo.dataDir + "/lib/${Constants.Executable.PROXYCHAINS4}")
			cmds.addFirst("PROXYCHAINS_CONF_FILE=" + applicationInfo.dataDir + "/proxychains.conf")
			cmds.addFirst("PROXYCHAINS_PROTECT_FD_PREFIX=" + applicationInfo.dataDir)
			cmds.addFirst("env")
		}

		VayLog.d(TAG, Utils.makeString(cmds, " "))

		try
		{
			sstunnelProcess = GuardedProcess(cmds)
				.start()
		}
		catch (e: InterruptedException)
		{
			e.printStackTrace()
		}
	}

	private fun startDnsDaemon()
	{
		val reject = if (profile!!.ipv6) "224.0.0.0/3" else "224.0.0.0/3, ::/0"
		val protect = "protect = \"$protectPath\";"

		val chinaDnsSettings = StringBuilder()

		var remoteDns = false

		if (Constants.Route.ACL == profile!!.route)
		{
			//decide acl route
			val totalLines = Utils.getLinesByFile(File("${applicationInfo.dataDir}/${profile!!.route}.acl"))
			for (line in totalLines)
			{
				if ("[remote_dns]" == line)
				{
					remoteDns = true
				}
			}
		}

		val blackList1 = when
		{
			Constants.Route.BYPASS_CHN == profile!!.route || Constants.Route.BYPASS_LAN_CHN == profile!!.route || Constants.Route.GFWLIST == profile!!.route -> blackList
			Constants.Route.ACL == profile!!.route && !remoteDns -> blackList
			else -> ""
		}

		for (china_dns in profile!!.china_dns.split(","))
		{
			chinaDnsSettings.append(String.format(Locale.ENGLISH, Constants.ConfigUtils.REMOTE_SERVER, china_dns.split(":")[0], Integer.parseInt(china_dns.split(":")[1]), blackList1, reject))
		}

		val conf = if (Constants.Route.BYPASS_CHN == profile!!.route || Constants.Route.BYPASS_LAN_CHN == profile!!.route || Constants.Route.GFWLIST == profile!!.route)
		{
			String.format(Locale.ENGLISH, Constants.ConfigUtils.PDNSD_DIRECT, protect, applicationInfo.dataDir, "0.0.0.0", profile!!.localPort + 53, chinaDnsSettings, profile!!.localPort + 63, reject)
		}
		else if (Constants.Route.CHINALIST == profile!!.route)
		{
			String.format(Locale.ENGLISH, Constants.ConfigUtils.PDNSD_DIRECT, protect, applicationInfo.dataDir, "0.0.0.0", profile!!.localPort + 53, chinaDnsSettings, profile!!.localPort + 63, reject)
		}
		else if (Constants.Route.ACL == profile!!.route)
		{
			if (!remoteDns)
			{
				String.format(Locale.ENGLISH, Constants.ConfigUtils.PDNSD_DIRECT, protect, applicationInfo.dataDir, "0.0.0.0", profile!!.localPort + 53, chinaDnsSettings, profile!!.localPort + 63, reject)
			}
			else
			{
				String.format(Locale.ENGLISH, Constants.ConfigUtils.PDNSD_LOCAL, protect, applicationInfo.dataDir, "0.0.0.0", profile!!.localPort + 53, profile!!.localPort + 63, reject)
			}
		}
		else
		{
			String.format(Locale.ENGLISH, Constants.ConfigUtils.PDNSD_LOCAL, protect, applicationInfo.dataDir, "0.0.0.0", profile!!.localPort + 53, profile!!.localPort + 63, reject)
		}

		Utils.printToFile(File(applicationInfo.dataDir + "/libpdnsd.so-vpn.conf"), conf)
		val cmd = arrayOf(applicationInfo.nativeLibraryDir + "/${Constants.Executable.PDNSD}", "-c", applicationInfo.dataDir + "/libpdnsd.so-vpn.conf")
		val cmds = listOf(*cmd)

		VayLog.d(TAG, Utils.makeString(cmds, " "))

		try
		{
			pdnsdProcess = GuardedProcess(cmds)
				.start()
		}
		catch (e: InterruptedException)
		{
			e.printStackTrace()
		}

	}

	private fun startVpn(): FileDescriptor
	{
		val builder = Builder()
		if (Build.VERSION.SDK_INT >= 29)
		{
			builder.setMetered(false)
		}

		builder.setSession(profile!!.name)
			.setMtu(VPN_MTU)
			.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24)

		if (Constants.Route.CHINALIST == profile!!.route)
		{
			builder.addDnsServer(chinaDnsAddress)
		}
		else
		{
			builder.addDnsServer(dnsAddress)
		}

		if (profile!!.ipv6)
		{
			builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126)
			builder.addRoute("::", 0)
		}

		if (Utils.isLollipopOrAbove)
		{
			if (profile!!.proxyApps)
			{
				for (pkg in profile!!.individual.split("\n"))
				{
					try
					{
						if (!profile!!.bypass)
						{
							builder.addAllowedApplication(pkg)
						}
						else
						{
							builder.addDisallowedApplication(pkg)
						}
					}
					catch (e: PackageManager.NameNotFoundException)
					{
						VayLog.e(TAG, "Invalid package name", e)
					}

				}
			}
		}

		if (Constants.Route.ALL == profile!!.route || Constants.Route.BYPASS_CHN == profile!!.route)
		{
			builder.addRoute("0.0.0.0", 0)
		}
		else
		{
			val privateList = resources.getStringArray(R.array.bypass_private_route)
			for (cidr in privateList)
			{
				val addr = cidr.split("/".toRegex())
					.dropLastWhile { it.isEmpty() }
					.toTypedArray()
				builder.addRoute(addr[0], Integer.parseInt(addr[1]))
			}
		}

		if (Constants.Route.CHINALIST == profile!!.route)
		{
			builder.addRoute(chinaDnsAddress, 32)
		}
		else
		{
			builder.addRoute(dnsAddress, 32)
		}

		val conn = builder.establish() ?: throw NullConnectionException()
		this.conn = conn

		val fd = conn.fd

		val cmd = arrayOf(applicationInfo.nativeLibraryDir + "/${Constants.Executable.TUN2SOCKS}", "--netif-ipaddr", String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"), "--netif-netmask", "255.255.255.0", "--socks-server-addr", "127.0.0.1:" + profile!!.localPort, "--tunfd", fd.toString(), "--tunmtu", VPN_MTU.toString(), "--sock-path", applicationInfo.dataDir + "/sock_path", "--loglevel", "3")

		val cmds = cmd.toMutableList()

		if (profile!!.ipv6)
		{
			cmds.add("--netif-ip6addr")
			cmds.add(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "2"))
		}

		if (profile!!.udpdns)
		{
			cmds.add("--enable-udprelay")
		}
		else
		{
			cmds.add("--dnsgw")
			cmds.add(String.format(Locale.ENGLISH, "%s:%d", String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), profile!!.localPort + 53))
		}

		VayLog.d(TAG, Utils.makeString(cmds, " "))

		try
		{
			tun2socksProcess = GuardedProcess(cmds)
				.start { sendFd(conn.fileDescriptor) }
		}
		catch (e: InterruptedException)
		{
			e.printStackTrace()
		}

		return conn.fileDescriptor
	}

	private fun sendFd(fd: FileDescriptor): Boolean
	{
		var tries = 0
		val path = File(applicationInfo.dataDir, "sock_path").absolutePath
		while (true)
		{
			try
			{
				Thread.sleep(50L shl tries)
				LocalSocket().use { localSocket ->
					localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
					localSocket.setFileDescriptorsForSend(arrayOf(fd))
					localSocket.outputStream.write(42)
				}
				return true
			}
			catch (e: IOException)
			{
				if (tries > 5)
				{
					return false
				}
				++tries
			}
		}
	}
}
