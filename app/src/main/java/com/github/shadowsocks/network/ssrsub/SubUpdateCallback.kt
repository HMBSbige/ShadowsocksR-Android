package com.github.shadowsocks.network.ssrsub


/**
 * Created by vay on 2018/07/19
 */
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
