package com.bige0.shadowsocksr

import android.annotation.*
import android.net.*
import android.system.*
import com.bige0.shadowsocksr.utils.*
import java.io.*
import java.util.concurrent.*

class ShadowsocksVpnThread(private val vpnService: ShadowsocksVpnService) : Thread()
{
	companion object
	{
		private const val TAG = "ShadowsocksVpnThread"
		private val PATH = BaseVpnService.protectPath

		@SuppressLint("DiscouragedPrivateApi")
		private val getInt = FileDescriptor::class.java.getDeclaredMethod("getInt$")
	}

	private var isRunning = true
	private var serverSocket: LocalServerSocket? = null
	private val pool: ScheduledExecutorService

	init
	{
		pool = ScheduledThreadPoolExecutor(4, ThreadFactory { r ->
			val thread = Thread(r)
			thread.name = TAG
			thread
		})
	}

	private fun closeServerSocket()
	{
		if (serverSocket != null)
		{
			try
			{
				serverSocket!!.close()
			}
			catch (e: Exception)
			{
				// Ignore
			}
			serverSocket = null
		}
	}

	fun stopThread()
	{
		isRunning = false
		closeServerSocket()
	}

	override fun run()
	{
		val deleteFlag = File(PATH).delete()
		VayLog.d(TAG, "run() delete file = $deleteFlag")

		if (!initServerSocket())
		{
			return
		}

		while (isRunning)
		{
			try
			{
				val socket = serverSocket!!.accept()
				// handle local socket
				handleLocalSocket(socket)
			}
			catch (e: IOException)
			{
				VayLog.e(TAG, "Error when accept socket", e)
				ShadowsocksApplication.app.track(e)

				initServerSocket()
			}
		}
	}

	/**
	 * handle local socket
	 *
	 * @param socket local socket object
	 */
	private fun handleLocalSocket(socket: LocalSocket)
	{
		pool.execute {
			try
			{
				val input = socket.inputStream
				val output = socket.outputStream

				// check read state
				val state = input.read()
				VayLog.d(TAG, "handleLocalSocket() read state = $state")

				val fds = socket.ancillaryFileDescriptors

				if (!fds.isNullOrEmpty())
				{
					val fd = getInt.invoke(fds[0]) as Int

					val ret = vpnService.protect(fd)

					Os.close(fds[0])
					output.write(if (ret) 0 else 1)
				}

				// close stream
				IOUtils.close(input)
				IOUtils.close(output)
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "handleLocalSocket() Error when protect socket", e)
				ShadowsocksApplication.app.track(e)
			}
			finally
			{
				try
				{
					socket.close()
				}
				catch (e: Exception)
				{
					// Ignore;
				}
			}
		}
	}

	/**
	 * init server socket
	 *
	 * @return init failed return false.
	 */
	private fun initServerSocket(): Boolean
	{
		// if not running, do not init
		if (!isRunning)
		{
			return false
		}

		return try
		{
			val localSocket = LocalSocket()
			localSocket.bind(LocalSocketAddress(PATH, LocalSocketAddress.Namespace.FILESYSTEM))
			serverSocket = LocalServerSocket(localSocket.fileDescriptor)
			true
		}
		catch (e: IOException)
		{
			VayLog.e(TAG, "unable to bind", e)
			ShadowsocksApplication.app.track(e)
			false
		}
	}
}
