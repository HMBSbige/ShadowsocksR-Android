package com.github.shadowsocks

object System
{
	init
	{
		java.lang.System.loadLibrary("system")
	}

	external fun getABI(): String

	external fun sendfd(fd: Int, path: String): Int

	external fun jniclose(fd: Int)
}
