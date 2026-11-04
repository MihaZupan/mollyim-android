/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

import kotlin.time.Duration

/**
 * [AppUpdatesSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class AppUpdatesSettingsState(
  val versionName: String = "",
  /** When we last successfully checked for an update, or [Duration.ZERO] if we never have. */
  val lastCheckedTime: Duration = Duration.ZERO,
  /** Molly periodically checks and asks before installing; this is its original preference. */
  val autoUpdateEnabled: Boolean = false,
  val includeBetaEnabled: Boolean = false,
  /** Website-managed Molly channel, not SDK/API-level auto-install support. */
  val manageMollyUpdates: Boolean = false
)

/** Existing Molly strings are supplied by the app, avoiding copied resources or Signal-updater wording. */
data class AppUpdatesSettingsLabels(
  val version: String,
  val storeManagedMessage: String,
  val autoUpdates: String,
  val autoUpdatesDescription: String,
  val includeBeta: String,
  val includeBetaDescription: String,
  val checkForUpdates: String,
  val lastChecked: String,
  val lastCheckedContentDescription: String = lastChecked
)
