package com.personal.mise

import com.personal.mise.ai.networkErrorDetail
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class NetworkErrorDetailTest {

    @Test
    fun listsCauseAndSuppressed() {
        val e = ConnectException("Failed to connect to example.com/1.2.3.4:443")
        e.initCause(IOException("connect failed: ENETUNREACH (Network is unreachable)"))
        e.addSuppressed(SocketTimeoutException("connect timed out"))
        assertEquals(
            "ConnectException: Failed to connect to example.com/1.2.3.4:443\n" +
                "SocketTimeoutException: connect timed out\n" +
                "IOException: connect failed: ENETUNREACH (Network is unreachable)",
            networkErrorDetail(e),
        )
    }

    @Test
    fun noMessage() {
        assertEquals("IOException", networkErrorDetail(IOException()))
    }
}
