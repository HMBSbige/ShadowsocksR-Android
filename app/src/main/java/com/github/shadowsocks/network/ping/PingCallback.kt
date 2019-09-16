package com.github.shadowsocks.network.ping


import com.github.shadowsocks.database.*

/**
 * ping callback
 *
 * Created by vay on 2018/07/18
 */
open class PingCallback
{

	/**
	 * test result message
	 */
	open var resultMsg: String = ""

	/**
	 * ping success
	 *
	 * @param elapsed ping elapsed
	 */
	open fun onSuccess(profile: Profile, elapsed: Long)
	{
	}

	/**
	 * ping failed
	 */
	open fun onFailed(profile: Profile?)
	{
	}

	/**
	 * ping finished
	 */
	open fun onFinished(profile: Profile?)
	{
	}
}
