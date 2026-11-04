/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import androidx.annotation.WorkerThread
import org.signal.core.util.net.resolveSystemProxy
import org.signal.libsignal.net.Network
import org.signal.network.config.ProxyConfig
import org.signal.network.config.ProxyConfig.ProxyScheme
import org.signal.network.config.ProxyConfig.ProxySource
import org.signal.network.config.configureProxy as applyCoreProxy
import org.thoughtcrime.securesms.BuildConfig
import java.net.InetSocketAddress
import java.net.Proxy

fun Proxy.toProxyConfig(source: ProxySource): ProxyConfig? {
  val sa = address() as? InetSocketAddress ?: return null
  val scheme = when (type()) {
    Proxy.Type.HTTP -> ProxyScheme.HTTP
    Proxy.Type.SOCKS -> when (source) {
      ProxySource.USER -> ProxyScheme.SOCKS_REMOTE_DNS
      ProxySource.SYSTEM -> ProxyScheme.SOCKS
    }
    Proxy.Type.DIRECT -> return ProxyConfig.Direct
  }
  return ProxyConfig.ProxyAddress(scheme, sa.hostString, sa.port, source)
}

/**
 * Resolves the appropriate proxy configuration or system defaults.
 * Falls back to direct connection if no valid proxy is found.
 *
 * @return The resolved proxy configuration
 */
@WorkerThread
fun resolveProxyConfig(): ProxyConfig {
  val userProxy = Networking.proxy
  if (userProxy == Proxy.NO_PROXY) {
    val systemProxy = resolveSystemProxy(targetUrl = BuildConfig.SIGNAL_URL)
    return systemProxy?.toProxyConfig(ProxySource.SYSTEM)
      ?: ProxyConfig.Direct
  }
  return userProxy.toProxyConfig(ProxySource.USER)!!
}

/**
 * Configures the [Network] instance with the given proxy settings.
 *
 * User proxies fail closed; invalid system proxies retain their existing direct fallback.
 *
 * System Proxies: the Android system settings screen explicitly calls out that apps are allowed
 * to ignore the proxy setting, so if configuration fails, we fall back to direct connection
 * rather than breaking connectivity.
 */
fun Network.configureProxy(config: ProxyConfig) {
  applyCoreProxy(config)
}
