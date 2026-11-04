/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface DataAndStorageSettingsAction {

  /** Leave the screen. */
  data object NavigateBack : DataAndStorageSettingsAction

  /** Open the manage storage screen. */
  data object NavigateToManageStorage : DataAndStorageSettingsAction

  /** Open Molly's connection/SOCKS/Orbot preferences, not Signal TLS proxy settings. */
  data object NavigateToNetwork : DataAndStorageSettingsAction
}
