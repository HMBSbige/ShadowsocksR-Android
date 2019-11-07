package com.bige0.shadowsocksr.utils

import android.content.*
import android.net.*
import com.bige0.shadowsocksr.*
import java.io.*
import java.nio.*
import java.util.concurrent.*

class TrafficMonitorThread(context: Context) : Thread()
{
	private val path: String = "${context.applicationInfo.dataDir}${File.separator}stat_path"
	private val pool: ScheduledExecutorService
	private var serverSocket: LocalServerSocket? = null
	private var isRunning = true

	init
	{
		pool = ScheduledThreadPoolExecutor(3, ThreadFactory { r ->
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
				// ignore
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
		val deleteResult = File(path).delete()
		VayLog.d(TAG, "run() delete file = $deleteResult")

		if (!initServerSocket())
		{
			return
		}

		while (isRunning)
		{
			try
			{
				val socket = serverSocket!!.accept()
				// handle socket
				handleLocalSocket(socket)
			}
			catch (e: Exception)
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
			val input: InputStream
			val output: OutputStream
			try
			{
				input = socket.inputStream
				output = socket.outputStream

				val buffer = ByteArray(16)
				if (input.read(buffer) != 16)
				{
					throw IOException("Unexpected traffic stat length")
				}
				val stat = ByteBuffer.wrap(buffer)
					.order(ByteOrder.LITTLE_ENDIAN)
				TrafficMonitor.update(stat.getLong(0), stat.getLong(8))
				output.write(0)
				output.flush()

				// close stream
				IOUtils.close(input)
				IOUtils.close(output)
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "handleLocalSocket() Error when recv traffic stat", e)
				ShadowsocksApplication.app.track(e)
			}
			finally
			{
				// close socket
				try
				{
					socket.close()
				}
				catch (e: IOException)
				{
					// ignore
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
			localSocket.bind(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
			serverSocket = LocalServerSocket(localSocket.fileDescriptor)
			true
		}
		catch (e: IOException)
		{
			VayLog.e(TAG, "unable to bind", e)
			false
		}

	}

	companion object
	{
		private const val TAG = "TrafficMonitorThread"
	}
}
