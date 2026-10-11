package com.personal.mise

import com.personal.mise.ai.AiHttp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiHttpTest {

    @Test
    fun connectFailuresCanRetry() {
        assertTrue(AiHttp.isConnectFailure(ConnectException("Failed to connect to example.com/1.2.3.4:443")))
        assertTrue(AiHttp.isConnectFailure(UnknownHostException("example.com")))
        assertTrue(AiHttp.isConnectFailure(SocketTimeoutException("Connect timed out")))
        assertTrue(AiHttp.isConnectFailure(IOException("wrapped", ConnectException("refused"))))
        assertTrue(AiHttp.isConnectFailure(IOException("outer").apply { addSuppressed(ConnectException("refused")) }))
    }

    @Test
    fun otherFailuresDoNotRetry() {
        // 读超时：请求可能已经到了服务器
        assertFalse(AiHttp.isConnectFailure(SocketTimeoutException("timeout")))
        assertFalse(AiHttp.isConnectFailure(IOException("unexpected end of stream")))
    }
}
