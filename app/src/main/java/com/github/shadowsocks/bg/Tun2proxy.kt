package com.github.shadowsocks.bg

object Tun2proxy {
    const val DNS_VIRTUAL: Int = 0
    const val DNS_OVER_TCP: Int = 1
    const val DNS_DIRECT: Int = 2

    const val VERBOSITY_OFF: Int = 0
    const val VERBOSITY_ERROR: Int = 1
    const val VERBOSITY_WARN: Int = 2
    const val VERBOSITY_INFO: Int = 3
    const val VERBOSITY_DEBUG: Int = 4
    const val VERBOSITY_TRACE: Int = 5

    init {
        System.loadLibrary("tun2proxy")
    }

    @JvmStatic
    external fun run(
        proxyUrl: String,
        tunFd: Int,
        closeFdOnDrop: Boolean,
        tunMtu: Char,
        verbosity: Int,
        dnsStrategy: Int
    ): Int

    @JvmStatic
    external fun stop(): Int
}
