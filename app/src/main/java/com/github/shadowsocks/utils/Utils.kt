package com.github.shadowsocks.utils

import android.animation.*
import android.annotation.*
import android.content.*
import android.content.pm.*
import android.graphics.*
import android.os.*
import android.text.*
import android.util.*
import android.view.*
import android.widget.*
import com.github.shadowsocks.*
import org.xbill.DNS.*
import java.io.*
import java.net.*
import java.security.*
import java.util.*
import kotlin.math.*

object Utils
{
	private const val TAG = "Shadowsocks"

	val isLollipopOrAbove: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

	/**
	 * If there exists a valid IPv6 interface
	 */
	private val isIPv6Support: Boolean
		get()
		{
			try
			{
				val networkInterfaces = NetworkInterface.getNetworkInterfaces()
				while (networkInterfaces.hasMoreElements())
				{
					val intf = networkInterfaces.nextElement()

					val addresses = intf.inetAddresses
					while (addresses.hasMoreElements())
					{
						val addr = addresses.nextElement()
						if (addr is Inet6Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress())
						{
							VayLog.d(TAG, "IPv6 address detected")
							return true
						}
					}
				}
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "Failed to get interfaces' addresses.", e)
				ShadowsocksApplication.app.track(e)
			}

			return false
		}

	@SafeVarargs
	fun <T> mergeList(vararg lists: Collection<T>?): List<T>
	{
		val result = ArrayList<T>()
		if (lists.isNullOrEmpty())
		{
			return emptyList()
		}
		for (list in lists)
		{
			if (list.isNullOrEmpty())
			{
				continue
			}
			result.addAll(list)
		}
		return result
	}

	/**
	 * use string divider list value
	 *
	 * @param list    list
	 * @param divider divider string
	 * @return list is empty, return null.
	 */
	fun makeString(list: Collection<String>?, divider: String): String
	{
		if (list.isNullOrEmpty())
		{
			return ""
		}
		return list.joinToString(divider)
	}

	fun getLinesByFile(file: File): List<String>
	{
		val list = ArrayList<String>()
		try
		{
			val inputStream: InputStream = file.inputStream()
			inputStream.bufferedReader()
				.useLines { lines -> lines.forEach { list.add(it) } }
		}
		catch (e: Exception)
		{
			// Ignore
		}

		return list
	}

	private fun bytesToHex(bytes: ByteArray): String
	{
		val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
		val hexChars = CharArray(bytes.size * 2)
		var v: Int
		for (j in bytes.indices)
		{
			v = bytes[j].toInt() and 0xFF
			hexChars[j * 2] = hexArray[v.ushr(4)]
			hexChars[j * 2 + 1] = hexArray[v and 0x0F]
		}
		return String(hexChars)
	}

	@Suppress("DEPRECATION")
	@SuppressLint("PackageManagerGetSignatures")
	fun getApplicationSignature(context: Context): List<String>
	{
		val signatureList: List<String>
		try
		{
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
			{
				// New signature
				val sig = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
					.signingInfo
				signatureList = if (sig.hasMultipleSigners())
				{
					// Send all with apkContentsSigners
					sig.apkContentsSigners.map {
						val digest = MessageDigest.getInstance("SHA")
						digest.update(it.toByteArray())
						bytesToHex(digest.digest())
					}
				}
				else
				{
					// Send one with signingCertificateHistory
					sig.signingCertificateHistory.map {
						val digest = MessageDigest.getInstance("SHA")
						digest.update(it.toByteArray())
						bytesToHex(digest.digest())
					}
				}
			}
			else
			{
				val sig = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
					.signatures
				signatureList = sig.map {
					val digest = MessageDigest.getInstance("SHA")
					digest.update(it.toByteArray())
					bytesToHex(digest.digest())
				}
			}

			return signatureList
		}
		catch (e: Exception)
		{
			// Handle error
		}
		return emptyList()
	}

	fun getSignature(context: Context): String?
	{
		try
		{
			/*val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
			val mdg = MessageDigest.getInstance("SHA-1")
			mdg.update(info.signatures[0].toByteArray())
			return String(Base64.encode(mdg.digest(), 0))*/
			return getApplicationSignature(context).first()
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getSignature", e)
			ShadowsocksApplication.app.track(e)
		}

		return null
	}

	fun dpToPx(context: Context, dp: Int): Int
	{
		return (dp * (context.resources.displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
	}

	/**
	 * round or floor depending on whether you are using offsets(floor) or
	 * widths(round)
	 *
	 *
	 * Based on: http://stackoverflow.com/a/21026866/2245107
	 */
	fun positionToast(toast: Toast, view: View, window: Window, offsetX: Int, offsetY: Int): Toast
	{
		val rect = Rect()
		window.decorView.getWindowVisibleDisplayFrame(rect)
		val viewLocation = IntArray(2)
		view.getLocationInWindow(viewLocation)
		val metrics = DisplayMetrics()
		window.windowManager.defaultDisplay.getMetrics(metrics)
		val toastView = toast.view
		toastView.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.UNSPECIFIED))
		toast.setGravity(Gravity.START or Gravity.TOP, viewLocation[0] - rect.left + (view.width - toast.view.measuredWidth) / 2 + offsetX, viewLocation[1] - rect.top + view.height + offsetY)
		return toast
	}

	fun crossFade(context: Context, from: View, to: View)
	{
		val shortAnimTime = context.resources.getInteger(android.R.integer.config_shortAnimTime)
		to.alpha = 0f
		to.visibility = View.VISIBLE
		to.animate()
			.alpha(1f)
			.duration = shortAnimTime.toLong()
		from.animate()
			.alpha(0f)
			.setDuration(shortAnimTime.toLong())
			.setListener(object : AnimatorListenerAdapter()
						 {
							 override fun onAnimationEnd(animation: Animator)
							 {
								 from.visibility = View.GONE
							 }
						 })
	}

	fun readAllLines(f: File): String?
	{
		var scanner: Scanner? = null
		try
		{
			scanner = Scanner(f)
			scanner.useDelimiter("\\Z")
			return scanner.next()
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "readAllLines", e)
			ShadowsocksApplication.app.track(e)
			return null
		}
		finally
		{
			scanner?.close()
		}
	}

	/**
	 * println to file
	 *
	 * @param file    file
	 * @param content string content
	 */
	@JvmOverloads //TODO: Drop Java
	fun printToFile(file: File, content: String, isPrintln: Boolean = false)
	{
		var p: PrintWriter? = null
		try
		{
			p = PrintWriter(file)
			if (isPrintln)
			{
				p.println(content)
			}
			else
			{
				p.print(content)
			}
			p.flush()
		}
		catch (e: Exception)
		{
			// Ignored
		}
		finally
		{
			p?.close()
		}
	}

	private fun resolve(host: String, addrType: Int): String?
	{
		try
		{
			val lookup = Lookup(host, addrType)
			val resolver = SimpleResolver("114.114.114.114")
			resolver.setTimeout(5)
			lookup.setResolver(resolver)
			val result = lookup.run()
			if (result.isNullOrEmpty())
			{
				return null
			}

			val records = ArrayList(listOf(*result))
			records.shuffle()
			for (r in records)
			{
				when (addrType)
				{
					Type.A -> return (r as ARecord).address.hostAddress
					Type.AAAA -> return (r as AAAARecord).address.hostAddress
					else ->
					{
					}
				}
			}
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "resolve", e)
			ShadowsocksApplication.app.track(e)
		}

		return null
	}

	private fun resolve(host: String): String?
	{
		try
		{
			val addr = InetAddress.getByName(host)
			return addr.hostAddress
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "resolve", e)
			ShadowsocksApplication.app.track(e)
		}

		return null
	}

	fun resolve(host: String, enableIPv6: Boolean): String?
	{
		var address: String?
		if (enableIPv6 && isIPv6Support)
		{
			address = resolve(host, Type.AAAA)
			if (!TextUtils.isEmpty(address))
			{
				return address
			}
		}

		address = resolve(host, Type.A)
		if (!TextUtils.isEmpty(address))
		{
			return address
		}

		address = resolve(host)
		return if (!TextUtils.isEmpty(address))
		{
			address
		}
		else null
	}

	fun isNumeric(address: String): Boolean
	{
		try
		{
			return Patterns.IP_ADDRESS.matcher(address)
				.matches()
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "isNumeric", e)
			ShadowsocksApplication.app.track(e)
		}

		return false
	}

	fun startSsService(context: Context)
	{
		val intent = Intent(context, ShadowsocksRunnerService::class.java)
		context.startService(intent)
	}

	fun stopSsService(context: Context)
	{
		val intent = Intent(Constants.Action.CLOSE)
		context.sendBroadcast(intent)
	}
}
