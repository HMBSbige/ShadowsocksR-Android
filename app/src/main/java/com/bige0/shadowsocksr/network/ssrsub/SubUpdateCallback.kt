package com.bige0.shadowsocksr.network.ssrsub

open class SubUpdateCallback
{
	/**
	 * success
	 */
	open fun onSuccess(subName: String)
	{
	}

	/**
	 * failed
	 */
	open fun onFailed()
	{
	}

	/**
	 * finished
	 */
	open fun onFinished()
	{
	}
}
