package com.bige0.shadowsocksr.network.request

import android.os.*
import android.text.*
import okhttp3.*
import java.util.concurrent.*

class RequestHelper private constructor(builder: OkHttpClient.Builder? = null)
{

	private var mClientBuilder: OkHttpClient.Builder? = null
	private var mClient: OkHttpClient? = null
	private val mThreadPool: ScheduledThreadPoolExecutor
	private val mUIHandler: Handler
	private val mDefaultRequestCallback: RequestCallback

	init
	{
		// create thread pool
		mThreadPool = ScheduledThreadPoolExecutor(10, ThreadFactory { r ->
			val thread = Thread(r)
			thread.name = "request_helper-thread"
			thread
		})

		// init handler
		mUIHandler = Handler(Looper.getMainLooper())

		// create default request callback
		mDefaultRequestCallback = RequestCallback()

		// set default builder
		setClientBuilder(builder)
	}

	/**
	 * network request by get
	 *
	 * @param url      request url
	 * @param callback request callback
	 */
	operator fun get(url: String, callback: RequestCallback)
	{
		val request = createRequest(url, callback) ?: return

		// request
		request(request, callback)
	}

	/**
	 * network request
	 *
	 * @param request  request object
	 * @param _callback request callback
	 */
	fun request(request: Request, _callback: RequestCallback?)
	{
		var callback = _callback
		// no allow callback is null
		if (callback == null)
		{
			callback = mDefaultRequestCallback
		}

		// start request task
		startRequestTask(request, callback)
	}

	/**
	 * request by thread
	 *
	 * @param request request object
	 * @return response object
	 */
	private fun requestByThread(request: Request): Response
	{
		return mClient!!.newCall(request)
			.execute()
	}

	//===================================================================================================//
	//========================================= private method =========================================//
	//=================================================================================================//

	/**
	 * create request object
	 */
	private fun createRequest(url: String, callback: RequestCallback): Request?
	{
		return createRequest(url, null, callback)
	}

	/**
	 * create request object
	 *
	 * @param _url  request url
	 * @param body request body
	 * @return create failed return null.
	 */
	private fun createRequest(_url: String, body: RequestBody?, callback: RequestCallback): Request?
	{
		var url = _url
		// format url string
		url = formatUrl(url)
		try
		{
			val builder = Request.Builder()
			builder.url(url)
			// body not null,  set body
			if (body != null)
			{
				builder.post(body)
			}
			return builder.build()
		}
		catch (e: Exception)
		{
			callback.onFailed(404, e.message!!)
			callback.onFinished()
			return null
		}

	}

	/**
	 * format url string
	 *
	 * @param _url request url
	 * @return url
	 */
	private fun formatUrl(_url: String): String
	{
		var url = _url
		url = url.replace(" ", "")
		url = url.replace("\n", "")
		url = url.replace("\r", "")
		return url
	}

	/**
	 * set client builder
	 *
	 * @param builder client builder object
	 */
	private fun setClientBuilder(builder: OkHttpClient.Builder?)
	{
		mClientBuilder = builder ?: mDefaultBuilder
		mClient = mClientBuilder!!.build()
	}

	/**
	 * open request task
	 *
	 * @param request  okhttp request object
	 * @param callback request callback
	 */
	private fun startRequestTask(request: Request, callback: RequestCallback)
	{
		mThreadPool.execute {
			var body: ResponseBody? = null
			try
			{
				val response = requestByThread(request)
				val code = response.code
				body = response.body
				var result: String? = null
				if (body != null)
				{
					result = body.string()
				}
				invokeRequestCallback(callback, code, result)
			}
			catch (e: Exception)
			{
				invokeRequestCallback(callback, 404, e.message)
			}
			finally
			{
				// close
				body?.close()

				// invoke finished
				invokeFinished(callback)
			}
		}
	}

	/**
	 * invoke request callback
	 *
	 * @param callback request callback
	 * @param code     response code
	 * @param response body
	 */
	private fun invokeRequestCallback(callback: RequestCallback?, code: Int, response: String?)
	{
		if (callback == null)
		{
			return
		}
		// run to main thread
		mUIHandler.post {
			try
			{
				if (callback.isRequestOk(code))
				{
					var result: String? = ""
					if (!TextUtils.isEmpty(response))
					{
						result = response
					}
					// invoke success callback
					callback.onSuccess(code, result!!)
				}
				else
				{
					var result: String? = "null"
					if (!TextUtils.isEmpty(response))
					{
						result = response
					}
					callback.onFailed(code, result!!)
				}
			}
			catch (e: Exception)
			{
				callback.onFailed(404, e.message!!)
			}
		}
	}

	/**
	 * invoke finished
	 *
	 * @param callback request callback
	 */
	private fun invokeFinished(callback: RequestCallback?)
	{
		if (callback == null)
		{
			return
		}
		// run to main thread
		mUIHandler.post { callback.onFinished() }
	}

	companion object
	{

		private var sInstance: RequestHelper? = null
		private val mDefaultBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
			.connectTimeout(5, TimeUnit.SECONDS)
			.writeTimeout(5, TimeUnit.SECONDS)
			.readTimeout(5, TimeUnit.SECONDS)
			.followRedirects(false)
			.followSslRedirects(false)

		/**
		 * init
		 *
		 * @param builder client builder object
		 */
		fun init(builder: OkHttpClient.Builder? = null)
		{
			if (sInstance == null)
			{
				synchronized(RequestHelper::class.java) {
					if (sInstance == null)
					{
						sInstance = RequestHelper(builder)
					}
				}
			}
		}

		/**
		 * get instance
		 */
		fun instance(): RequestHelper?
		{
			init()
			return sInstance
		}
	}
}
/**
 * init
 *
 * @see .init
 */
