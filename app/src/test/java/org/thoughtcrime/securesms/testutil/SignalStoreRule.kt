/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Environment

/**
 * Installs a real [SignalStore] backed by in-memory storage, letting tests arrange and assert on actual
 * stored state rather than stubbing interactions. Use [MockSignalStoreRule] instead when you need
 * interaction-based mocking.
 */
class SignalStoreRule : ExternalResource() {

  override fun before() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val wasInstrumentation = Environment.IS_INSTRUMENTATION
    Environment.IS_INSTRUMENTATION = true
    try {
      SignalStore.testInject(SignalStore(application, KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()), DirectExecutor())))
    } finally {
      Environment.IS_INSTRUMENTATION = wasInstrumentation
    }
  }

  override fun after() {
    SignalStore.testInject(null)
  }
}
