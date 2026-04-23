package com.privatevpn.app.vpn.dataplane

import com.github.shadowsocks.bg.Tun2proxy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class Tun2ProxyDataPlane(
    private val log: (String) -> Unit
) {
    private val lock = Any()

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var lastExitCode: Int? = null

    fun start(
        socksHost: String,
        socksPort: Int,
        socksUsername: String,
        socksPassword: String,
        tunFd: Int,
        mtu: Int
    ): Result<Unit> {
        synchronized(lock) {
            if (running) {
                if (workerThread?.isAlive == true) {
                    return Result.failure(IllegalStateException("tun2proxy уже запущен"))
                }
                running = false
                workerThread = null
                lastExitCode = null
                log("tun2proxy: обнаружено зависшее состояние running, выполнен принудительный reset локального состояния")
            }

            if (workerThread?.isAlive == true) {
                return Result.failure(IllegalStateException("tun2proxy уже запущен"))
            }

            require(socksPort in 1..65535) { "Некорректный порт data plane SOCKS" }
            require(socksUsername.isNotBlank() && socksPassword.isNotBlank()) {
                "Для data plane SOCKS обязательны логин и пароль"
            }

            val proxyUrl = buildProxyUrl(
                host = socksHost,
                port = socksPort,
                username = socksUsername,
                password = socksPassword
            )

            return runCatching {
                running = true
                lastExitCode = null
                val thread = Thread {
                    val code = runCatching {
                        Tun2proxy.run(
                            proxyUrl,
                            tunFd,
                            false,
                            mtu.toChar(),
                            Tun2proxy.VERBOSITY_INFO,
                            Tun2proxy.DNS_VIRTUAL
                        )
                    }.getOrElse { error ->
                        log("tun2proxy ошибка запуска: ${error.message ?: "неизвестная ошибка"}")
                        -1
                    }

                    synchronized(lock) {
                        lastExitCode = code
                        running = false
                        if (workerThread === Thread.currentThread()) {
                            workerThread = null
                        }
                    }
                    log("tun2proxy завершён, код $code")
                }.apply {
                    name = "privatevpn-tun2proxy"
                    isDaemon = true
                    start()
                }

                workerThread = thread
                Thread.sleep(350)
                if (!thread.isAlive) {
                    throw IllegalStateException(
                        "tun2proxy завершился сразу после старта (код ${lastExitCode ?: -1})"
                    )
                }

                log("tun2proxy запущен: proxy=$socksHost:$socksPort")
            }.onFailure {
                running = false
                workerThread = null
            }
        }
    }

    fun isRunning(): Boolean = synchronized(lock) {
        running && workerThread?.isAlive == true
    }

    fun stop() {
        val threadToJoin: Thread? = synchronized(lock) { workerThread }

        runCatching {
            Tun2proxy.stop()
        }.onFailure { error ->
            log("tun2proxy stop завершился ошибкой: ${error.message ?: "неизвестная ошибка"}")
        }

        threadToJoin?.let { thread ->
            runCatching {
                thread.join(TimeUnit.SECONDS.toMillis(2))
            }
        }

        synchronized(lock) {
            workerThread = null
            running = false
        }
    }

    private fun buildProxyUrl(
        host: String,
        port: Int,
        username: String,
        password: String
    ): String {
        val encodedUser = encodeUserInfo(username)
        val encodedPassword = encodeUserInfo(password)
        return "socks5://$encodedUser:$encodedPassword@$host:$port"
    }

    private fun encodeUserInfo(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
    }
}
