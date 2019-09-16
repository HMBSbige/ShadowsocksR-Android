package com.github.shadowsocks.network.ping


import com.github.shadowsocks.database.Profile

/**
 * ping callback
 *
 * Created by vay on 2018/07/18
 */
open class PingCallback() {

    /**
     * get test result message
     */
    /**
     * set test result message
     *
     * @param resultMsg test result message
     */
    open var resultMsg: String = ""
    get() {return field}
    set(value) {field = value}

    /**
     * ping success
     *
     * @param elapsed ping elapsed
     */
    open fun onSuccess(profile: Profile, elapsed: Long) {
    }

    /**
     * ping failed
     */
    open fun onFailed(profile: Profile?) {}

    /**
     * ping finished
     */
    open fun onFinished(profile: Profile?) {}
}
