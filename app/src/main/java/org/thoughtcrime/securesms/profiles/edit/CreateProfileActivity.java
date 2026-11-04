package org.thoughtcrime.securesms.profiles.edit;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import org.signal.registration.RegistrationActivityLifecycle;
import org.thoughtcrime.securesms.PassphraseActivity;
import org.thoughtcrime.securesms.PassphraseCreateActivity;
import org.thoughtcrime.securesms.PassphrasePromptActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Shows editing screen for your profile during registration. Also handles group name editing.
 */
public class CreateProfileActivity extends PassphraseActivity implements CreateProfileFragment.Controller {

  private static final String FRAGMENT_STATE = "create_profile_fragment_state";
  private static final String ACTIVITY_RESULT_STATE = "create_profile_activity_result_state";

  public static final String NEXT_INTENT            = "next_intent";
  public static final String EXCLUDE_SYSTEM         = "exclude_system";
  public static final String NEXT_BUTTON_TEXT       = "next_button_text";
  public static final String SHOW_TOOLBAR           = "show_back_arrow";
  public static final String GROUP_ID               = "group_id";
  public static final String IS_DESCRIPTION_FOCUSED = "is_description_focused";

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  private @Nullable RegistrationActivityLifecycle activityLifecycle;
  private @Nullable Fragment.SavedState pendingFragmentState;
  private boolean contentInitialized;

  public static @NonNull Intent getIntentForUserProfile(@NonNull Context context) {
    Intent intent = new Intent(context, CreateProfileActivity.class);
    intent.putExtra(CreateProfileActivity.SHOW_TOOLBAR, false);
    return intent;
  }

  public static @NonNull Intent getIntentForGroupProfile(@NonNull Context context, @NonNull GroupId groupId) {
    Intent intent = new Intent(context, CreateProfileActivity.class);
    intent.putExtra(CreateProfileActivity.SHOW_TOOLBAR, true);
    intent.putExtra(CreateProfileActivity.GROUP_ID, groupId.toString());
    intent.putExtra(CreateProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
    return intent;
  }

  public static @NonNull Intent getIntentForGroupProfileWithFocusedDescription(@NonNull Context context, @NonNull GroupId groupId) {
    Intent intent = new Intent(context, CreateProfileActivity.class);
    intent.putExtra(CreateProfileActivity.SHOW_TOOLBAR, true);
    intent.putExtra(CreateProfileActivity.GROUP_ID, groupId.toString());
    intent.putExtra(CreateProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
    intent.putExtra(CreateProfileActivity.IS_DESCRIPTION_FOCUSED, true);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    dynamicTheme.onCreate(this);
    activityLifecycle = RegistrationActivityLifecycle.Companion.create(this);

    // Framework restoration can instantiate private fragments before onPostResume admission.
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

    if (activityLifecycle == null) {
      finishAndRemoveTask();
      return;
    }

    Class<?> destination = !MasterSecretUtil.isPassphraseInitialized(this) ? PassphraseCreateActivity.class
                           : KeyCachingService.isLocked()                ? PassphrasePromptActivity.class
                                                                         : null;
    if (destination != null) {
      startActivity(new Intent(this, destination).putExtra(NEXT_INTENT, new Intent(getIntent())));
      finish();
      return;
    }

    if (bundle != null) {
      pendingFragmentState = BundleCompat.getParcelable(bundle, FRAGMENT_STATE, Fragment.SavedState.class);
      getActivityResultRegistry().onRestoreInstanceState(bundle.getBundle(ACTIVITY_RESULT_STATE));
    }
  }

  @Override
  protected void onPostResume(boolean screenLocked) {
    super.onPostResume(screenLocked);

    if (isFinishing() || screenLocked || activityLifecycle == null || !activityLifecycle.isAdmitted() || contentInitialized) {
      return;
    }

    setContentView(R.layout.create_profile_activity);
    NavHostFragment fragment = NavHostFragment.create(R.navigation.create_profile, getIntent().getExtras());
    if (pendingFragmentState != null) {
      fragment.setInitialSavedState(pendingFragmentState);
    }
    getSupportFragmentManager().beginTransaction()
                               .add(R.id.fragment_container, fragment)
                               .commitNow();
    pendingFragmentState = null;
    contentInitialized = true;

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (!Navigation.findNavController(CreateProfileActivity.this, R.id.fragment_container).popBackStack()) {
          finish();
        }
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    if (activityLifecycle != null) {
      activityLifecycle.onResume();
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (fragment != null && fragment.isAdded()) {
      pendingFragmentState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
    }
    outState.putParcelable(FRAGMENT_STATE, pendingFragmentState);
    Bundle resultState = new Bundle();
    getActivityResultRegistry().onSaveInstanceState(resultState);
    outState.putBundle(ACTIVITY_RESULT_STATE, resultState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    if (activityLifecycle != null) {
      activityLifecycle.onDestroy();
      activityLifecycle = null;
    }
    super.onDestroy();
  }

  @Override
  public void onProfileNameUploadCompleted() {
    setResult(RESULT_OK);
    finish();
  }
}
