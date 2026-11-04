package org.signal.appsettings.appicon

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AppIconPresetTest {
  @Test
  fun `disabled launcher aliases use their installed Molly icon and label`() {
    val context = mockk<Context>()
    val manager = mockk<PackageManager>()
    val info = mockk<ActivityInfo>()
    every { context.applicationContext } returns context
    every { context.packageName } returns "im.molly.aliasfixture"
    every { context.packageManager } returns manager
    every { info.iconResource } returns 123
    every { info.loadLabel(manager) } returns "Molly alias"
    val component = ComponentName("im.molly.aliasfixture", "org.thoughtcrime.securesms.RoutingActivityAltWhite")
    every { manager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS) } returns info

    assertEquals(123, AppIconPreset.WHITE.getIconPreviewResourceId(context))
    assertEquals("Molly alias", AppIconPreset.WHITE.getLauncherLabel(context))
  }

  @Test
  fun `module previews without app aliases retain their local fallback`() {
    val context = mockk<Context>()
    val manager = mockk<PackageManager>()
    every { context.applicationContext } returns context
    every { context.packageName } returns "org.signal.appsettings"
    every { context.packageManager } returns manager
    every { manager.getActivityInfo(any<ComponentName>(), PackageManager.MATCH_DISABLED_COMPONENTS) } throws PackageManager.NameNotFoundException()

    assertEquals(AppIconPreset.DEFAULT.iconPreviewResId, AppIconPreset.DEFAULT.getIconPreviewResourceId(context))
    assertNull(AppIconPreset.DEFAULT.getLauncherLabel(context))
  }
}
