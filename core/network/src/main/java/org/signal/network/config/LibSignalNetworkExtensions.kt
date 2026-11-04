/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibSignalNetworkExtensions")

package org.signal.network.config

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.Network
import java.io.IOException

private const val TAG = "LibSignalNetworkExtensions"

/**
 * Helper method to apply settings from the SignalServiceConfiguration.
 */
fun Network.applyConfiguration(config: SignalServiceConfiguration) {
  this.setCensorshipCircumventionEnabled(config.censored)
}

/**
 * Configures the [Network] instance with the given proxy settings.
 *
 * User proxies fail closed. Signal TLS proxies remain unsupported.
 *
 * System Proxies: the Android system settings screen explicitly calls out that apps are allowed
 * to ignore the proxy setting, so if configuration fails, we fall back to direct connection
 * rather than breaking connectivity.
 */
fun Network.configureProxy(config: ProxyConfig) {
  when (config) {
    ProxyConfig.Direct -> {
      Log.i(TAG, "No proxy configured.")
      clearProxy()
    }

    is ProxyConfig.ProxyAddress -> {
      if (config.scheme == ProxyConfig.ProxyScheme.TLS) {
        Log.e(TAG, "Signal TLS proxies are unsupported.")
        setInvalidProxy()
        return
      }

      try {
        setProxy(config.scheme.value, config.host, config.port, null, null)
        Log.i(TAG, "Proxy configured: ${config.scheme}/${config.port}")
      } catch (e: IOException) {
        when (config.source) {
          ProxyConfig.ProxySource.USER -> {
            Log.e(TAG, "Invalid user proxy config! Failing connections until changed.", e)
            setInvalidProxy()
          }

          ProxyConfig.ProxySource.SYSTEM -> {
            Log.w(TAG, "Failed to configure ${config.scheme} proxy, falling back to direct connection.", e)
            clearProxy()
          }
        }
      }
    }
  }
}
