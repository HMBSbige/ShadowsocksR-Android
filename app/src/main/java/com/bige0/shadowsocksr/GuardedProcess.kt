package com.bige0.shadowsocksr

import android.util.*
import com.bige0.shadowsocksr.utils.*
import java.io.*
import java.util.concurrent.*

class GuardedProcess(private val cmd: List<String>)
{
	private var guardThread: Thread? = null
	private var isDestroyed: Boolean = false
	private var process: Process? = null
	private var isRestart = false

	fun start(onRestartCallback: (() -> Boolean)? = null): GuardedProcess
	{
		val semaphore = Semaphore(1)
		semaphore.acquire()

		guardThread = Thread(object : Runnable
							 {
								 override fun run()
								 {
									 try
									 {
										 var callback: (() -> Boolean)? = null
										 while (!isDestroyed)
										 {
											 VayLog.i(TAG, "start process: $cmd")
											 val startTime = System.currentTimeMillis()

											 process = ProcessBuilder(cmd).redirectErrorStream(true)
												 .start()

											 val inputStream = process!!.inputStream
											 StreamLogger(inputStream, TAG).start()

											 if (callback == null)
											 {
												 callback = onRestartCallback
											 }
											 else
											 {
												 callback()
											 }

											 semaphore.release()
											 process!!.waitFor()

											 synchronized(this) {
												 if (isRestart)
												 {
													 isRestart = false
												 }
												 else
												 {
													 if (System.currentTimeMillis() - startTime < 1000)
													 {
														 Log.w(TAG, "process exit too fast, stop guard: $cmd")
														 isDestroyed = true
													 }
												 }
											 }

										 }
									 }
									 catch (ignored: Exception)
									 {
										 VayLog.i(TAG, "thread interrupt, destroy process: $cmd")
										 if (process != null)
										 {
											 process!!.destroy()
										 }
									 }
									 finally
									 {
										 semaphore.release()
									 }
								 }
							 }, "GuardThread-$cmd")

		guardThread!!.start()
		semaphore.acquire()
		return this
	}

	fun destroy()
	{
		isDestroyed = true
		guardThread?.interrupt()
		process?.destroy()
		try
		{
			guardThread?.join()
		}
		catch (e: InterruptedException)
		{
			// Ignored
		}
	}

	inner class StreamLogger(private val inputStream: InputStream, private val tag: String) : Thread()
	{
		override fun run()
		{
			var bufferedReader: BufferedReader? = null
			try
			{
				bufferedReader = BufferedReader(InputStreamReader(inputStream))
				var temp = bufferedReader.readLine()
				while (temp != null)
				{
					VayLog.e(tag, temp)
					temp = bufferedReader.readLine()
				}
			}
			catch (e: Exception)
			{
				// Ignore
			}
			finally
			{
				try
				{
					bufferedReader?.close()
				}
				catch (e: IOException)
				{
					// Ignore
				}
			}
		}
	}

	companion object
	{
		private const val TAG = "GuardedProcess"
	}
}
