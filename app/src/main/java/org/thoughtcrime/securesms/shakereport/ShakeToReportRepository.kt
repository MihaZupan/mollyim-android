/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

class ShakeToReportRepository(
  private val submitDebugLogRepository: SubmitDebugLogRepository = SubmitDebugLogRepository()
) {

  suspend fun submitLog(): SubmitResult {
    // Molly does not provide the automatic shake-report upload backend.
    return SubmitResult.Failure
  }

  sealed interface SubmitResult {
    data class Success(val url: String) : SubmitResult
    data object Failure : SubmitResult
  }
}
