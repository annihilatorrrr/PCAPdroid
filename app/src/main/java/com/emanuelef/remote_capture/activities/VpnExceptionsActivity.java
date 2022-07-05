/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PCAPdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PCAPdroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2020-22 - Emanuele Faranda
 */

package com.emanuelef.remote_capture.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.emanuelef.remote_capture.R;
import com.emanuelef.remote_capture.fragments.AppsToggles;
import com.emanuelef.remote_capture.model.AppDescriptor;
import com.emanuelef.remote_capture.model.Prefs;

import java.util.HashSet;
import java.util.Set;

public class VpnExceptionsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.vpn_exceptions);
        setContentView(R.layout.fragment_activity);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, new VpnExceptionsFragment())
                .commit();
    }

    public static class VpnExceptionsFragment extends AppsToggles {
        private static final String TAG = "VpnExceptions";
        private final Set<String> mExcludedApps = new HashSet<>();
        private @Nullable SharedPreferences mPrefs;

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            assert mPrefs != null;

            mExcludedApps.clear();
            Set<String> saved = mPrefs.getStringSet(Prefs.PREF_VPN_EXCEPTIONS, null);
            if(saved != null) {
                Log.d(TAG, "Loading " + saved.size() + " exceptions");
                mExcludedApps.addAll(saved);
            }
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mPrefs = null;
        }

        @Override
        protected Set<String> getCheckedApps() {
            return mExcludedApps;
        }

        @Override
        public void onAppToggled(AppDescriptor app, boolean checked) {
            String packageName = app.getPackageName();
            if(mExcludedApps.contains(packageName) == checked)
                return; // nothing to do

            if(checked)
                mExcludedApps.add(packageName);
            else
                mExcludedApps.remove(packageName);

            Log.d(TAG, "Saving " + mExcludedApps.size() + " exceptions");

            if(mPrefs == null)
                return;

            mPrefs.edit()
                    .putStringSet(Prefs.PREF_VPN_EXCEPTIONS, mExcludedApps)
                    .apply();
        }
    }
}
