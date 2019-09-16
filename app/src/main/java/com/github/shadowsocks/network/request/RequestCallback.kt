package com.github.shadowsocks.network.request


/**
 * request callback
 *
 * Created by vay on 2018/07/18
 */
open class RequestCallback {

    var start:Long = 0
        get() {
            return field
        }
        set(value) {
            field = value
        }


    /**
     * request success
     *
     * @param code     response code
     * @param response response result
     */
    open fun onSuccess(code: Int, response: String) {}

    /**
     * request failed
     *
     * @param code failed code
     * @param msg  failed msg
     */
    open fun onFailed(code: Int, msg: String) {}

    /**
     * request finished
     */
    open fun onFinished() {}

    /**
     * is request ok
     *
     * @param code response code
     */
    open fun isRequestOk(code: Int): Boolean {
        return code == 200||code==204
    }
}
