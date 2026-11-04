package org.thoughtcrime.securesms.preferences;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Objects;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    PreferenceDataStore dataStore = new SharedPreferencesDataStore(
            TextSecurePreferences.getSharedPreferences(requireContext()));
    getPreferenceManager().setPreferenceDataStore(dataStore);
    onCreateEncryptedPreferences(savedInstanceState, rootKey);
  }

  protected abstract void onCreateEncryptedPreferences(Bundle savedInstanceState, String rootKey);

  @Override
  @SuppressLint("RestrictedApi")
  protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
    return new PreferenceGroupAdapter(preferenceScreen) {
      @Override
      public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference preference = getItem(position);
        View iconFrame = holder.itemView.findViewById(androidx.preference.R.id.icon_frame);
        if (iconFrame != null) {
          iconFrame.setVisibility(preference.getIcon() == null ? View.GONE : View.VISIBLE);
        }
      }
    };
  }

  public <T extends Preference> T requirePreference(@NonNull CharSequence key) {
    return Objects.requireNonNull(super.findPreference(key));
  }

  @Nullable
  protected ActionBar getSupportActionBar() {
    if (getActivity() instanceof AppCompatActivity) {
      return ((AppCompatActivity) getActivity()).getSupportActionBar();
    } else {
      return null;
    }
  }
}
