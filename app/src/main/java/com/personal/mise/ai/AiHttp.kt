package com.personal.mise.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.personal.mise.importer.ImportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** 服务器的回应：状态码和正文 */
class HttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * 发 AI 请求。先走系统当前的网络；如果连都连不上（比如某个 Wi-Fi 到 Google 不通），
 * 且设置里打开了「Wi-Fi 连不上时改用移动数据」，就单独申请一条移动数据网络再发一次。
 */
class AiHttp(
    private val settings: AiSettings,
    private val cellular: CellularNetwork,
    readTimeoutSeconds: Long,
    /** 改用移动数据并且成功时调用，用来提示一下用了流量 */
    private val onCellularUsed: () -> Unit = {},
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    /** [name] 是服务商名字，用在出错提示里 */
    suspend fun execute(request: Request, name: String): HttpReply = withContext(Dispatchers.IO) {
        try {
            call(client, request)
        } catch (e: IOException) {
            if (!isConnectFailure(e) || !settings.cellularFallback || cellular.isDefaultCellular) {
                throw AiNetworkException("连接 $name 失败：${networkErrorDetail(e)}")
            }
            val reply = try {
                cellular.use { network ->
                    val viaCellular = client.newBuilder()
                        .socketFactory(network.socketFactory)
                        .dns(object : Dns {
                            override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
                        })
                        .build()
                    call(viaCellular, request)
                }
            } catch (e2: IOException) {
                throw AiNetworkException("连接 $name 失败，改用移动数据也没连上：${networkErrorDetail(e2)}")
            } ?: throw AiNetworkException("连接 $name 失败，也没有可用的移动数据：${networkErrorDetail(e)}")
            onCellularUsed()
            reply
        }
    }

    private fun call(client: OkHttpClient, request: Request): HttpReply =
        client.newCall(request).execute().use { HttpReply(it.code, it.body.string()) }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 8L

        /**
         * 是不是连接阶段就失败了（连不上、找不到主机、连接超时）。
         * 这种情况请求还没发出去，换网络重发是安全的；读超时之类请求可能已经到了服务器，不重发。
         */
        fun isConnectFailure(e: Throwable): Boolean {
            val seen = mutableSetOf<Throwable>()
            fun check(t: Throwable?): Boolean {
                if (t == null || !seen.add(t)) return false
                val hit = when (t) {
                    is ConnectException, is NoRouteToHostException, is UnknownHostException -> true
                    is SocketTimeoutException -> t.message.orEmpty().contains("connect", ignoreCase = true)
                    else -> false
                }
                return hit || check(t.cause) || t.suppressed.any { check(it) }
            }
            return check(e)
        }
    }
}

/** 网络出错：连不上、DNS 查不到、等回复超时；服务器回了错误码不算 */
class AiNetworkException(message: String) : ImportException(message)

/** 在 Wi-Fi 连着的时候，单独借用一条移动数据网络 */
class CellularNetwork(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    /** 系统当前本来就在用移动数据，再申请一次也是同一条，没必要重试 */
    val isDefaultCellular: Boolean
        get() = runCatching {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }.getOrDefault(false)

    /** 申请移动数据网络，在上面执行 [block]，用完释放；申请不到（没插卡、关了移动数据）时返回 null */
    suspend fun <T> use(block: suspend (Network) -> T): T? {
        val available = CompletableDeferred<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.complete(network)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        if (runCatching { cm.requestNetwork(request, callback) }.isFailure) return null
        try {
            val network = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { available.await() } ?: return null
            return block(network)
        } finally {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
