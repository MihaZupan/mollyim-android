/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.net.Uri
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.service.KeyCachingService

/**
 * Read-only access to Molly's original preferences, after the master secret has been cached.
 * Never use the compatibility facade here: it reads the destination rather than the migration source.
 */
object LegacySharedPrefs {

  fun contains(context: Context, key: String): Boolean {
    return prefs(context).contains(key)
  }

  fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
    return prefs(context).getBoolean(key, defaultValue)
  }

  fun getInteger(context: Context, key: String, defaultValue: Int): Int {
    return prefs(context).getInt(key, defaultValue)
  }

  fun getLong(context: Context, key: String, defaultValue: Long): Long {
    return prefs(context).getLong(key, defaultValue)
  }

  fun getString(context: Context, key: String, defaultValue: String): String {
    return prefs(context).getString(key, defaultValue) ?: defaultValue
  }

  fun getStringOrNull(context: Context, key: String): String? {
    return prefs(context).getString(key, null)
  }

  /** Some values were stored as strings, but some installs ended up with ints. */
  fun getIntegerFromString(context: Context, key: String, defaultValue: Int): Int {
    val stringValue = try {
      // EncryptedPreferences returns the default (rather than throwing) on a type mismatch.
      prefs(context).getString(key, null)
    } catch (e: ClassCastException) {
      null
    }
    return if (stringValue != null) stringValue.toIntOrNull() ?: defaultValue else getInteger(context, key, defaultValue)
  }

  /** Mirrors the URI fixup the old ringtone getters did. */
  fun getRingtone(context: Context, key: String, defaultValue: Uri): String {
    val result = getString(context, key, defaultValue.toString())

    return if (result.startsWith("file:")) {
      defaultValue.toString()
    } else {
      result
    }
  }

  fun getStringSet(context: Context, key: String, defaultValues: Set<String>?): Set<String>? {
    return if (prefs(context).contains(key)) {
      prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    } else {
      defaultValues
    }
  }

  private fun prefs(context: Context): android.content.SharedPreferences {
    if (!Environment.IS_INSTRUMENTATION) {
      checkNotNull(KeyCachingService.getMasterSecret()) { "Legacy settings migration requires an unlocked master secret" }
    }
    return SecurePreferenceManager.getSecurePreferences(context)
  }
}
