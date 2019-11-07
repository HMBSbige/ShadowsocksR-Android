package com.bige0.shadowsocksr.utils

import java.io.*
import java.net.*

object IOUtils
{
	private const val TAG = "IOUtils"
	private const val BUFFER_SIZE = 32 * 1024

	/**
	 * inputStream copy to out
	 *
	 * @param inputStream  input stream
	 * @param out output stream
	 */
	fun copy(inputStream: InputStream, out: OutputStream)
	{
		val buffer = ByteArray(BUFFER_SIZE)
		var temp: Int
		while (true)
		{
			temp = inputStream.read(buffer)
			if (temp == -1)
			{
				break
			}
			out.write(buffer, 0, temp)
		}
		out.flush()
	}

	/**
	 * read string by input stream
	 *
	 * @param `inputStream` input stream
	 * @return read failed return ""
	 */
	fun readString(inputStream: InputStream): String
	{
		val builder = StringBuilder()
		val buffer = ByteArray(BUFFER_SIZE)
		var temp: Int
		while (true)
		{
			temp = inputStream.read(buffer)
			if (temp == -1)
			{
				break
			}
			builder.append(String(buffer, 0, temp))
		}
		return builder.toString()
	}

	/**
	 * write string
	 *
	 * @param file    file path
	 * @param content string content
	 * @return write failed return false.
	 */
	fun writeString(file: String, content: String): Boolean
	{
		var writer: FileWriter? = null
		try
		{
			writer = FileWriter(file)
			writer.write(content)
			return true
		}
		catch (e: IOException)
		{
			VayLog.e(TAG, "writeString", e)
			return false
		}
		finally
		{
			try
			{
				writer?.close()
			}
			catch (e: IOException)
			{
				e.printStackTrace()
			}
		}
	}

	fun close(inputStream: InputStream?)
	{
		try
		{
			inputStream?.close()
		}
		catch (e: IOException)
		{
			// ignored
		}
	}

	fun close(outputStream: OutputStream?)
	{
		try
		{
			outputStream?.close()
		}
		catch (e: IOException)
		{
			// Ignored
		}
	}

	fun close(writer: Writer?)
	{
		try
		{
			writer?.close()
		}
		catch (e: IOException)
		{
			// Ignored
		}
	}

	fun close(reader: Reader?)
	{
		try
		{
			reader?.close()
		}
		catch (e: IOException)
		{
			// Ignored
		}
	}

	fun disconnect(conn: HttpURLConnection?)
	{
		conn?.disconnect()
	}
}
