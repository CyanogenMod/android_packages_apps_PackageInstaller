/*
* Copyright (C) 2015 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.packageinstaller.permission.ui;

import android.annotation.Nullable;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissionsFragment extends SettingsWithHeader
        implements OnPreferenceChangeListener {

    private static final String LOG_TAG = "ManagePermsFragment";

    static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";
    public static final String ARG_ADDITIONAL_PAGE = "additionalPage";

    private static final int MENU_ALL_PERMS = 0;
    public static final String RUNTIME_PERMS_CAT = "runtime_perms";

    private List<AppPermissionGroup> mToggledGroups;
    private AppPermissions mAppPermissions;

    private boolean mHasConfirmedRevoke;

    private AppOpsManager mAppOps;

    // mode which shows the "additional permissions"
    private boolean mAdditionalPageMode = false;

    public static AppPermissionsFragment newInstance(String packageName) {
        return setPackageName(new AppPermissionsFragment(), packageName);
    }

    private static <T extends Fragment> T setPackageName(T fragment, String packageName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        Activity activity = getActivity();
        PackageInfo packageInfo = getPackageInfo(activity, packageName);
        if (packageInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        mAdditionalPageMode = getArguments().getBoolean(ARG_ADDITIONAL_PAGE);
        mAppOps = (AppOpsManager) getActivity().getSystemService(Context.APP_OPS_SERVICE);
        mAppPermissions = new AppPermissions(activity, packageInfo, null, true, new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        });
        loadPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppPermissions.refresh();
        setPreferencesCheckedState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getActivity().finish();
                return true;
            }

            case MENU_ALL_PERMS: {
                Fragment frag = AllAppPermissionsFragment.newInstance(
                        getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack("AllPerms")
                        .commit();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mAppPermissions != null) {
            bindUi(this, mAppPermissions.getPackageInfo());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_ALL_PERMS, Menu.NONE, R.string.all_permissions);
    }

    private static void bindUi(SettingsWithHeader fragment, PackageInfo packageInfo) {
        Activity activity = fragment.getActivity();
        PackageManager pm = activity.getPackageManager();
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        Intent infoIntent = null;
        if (!activity.getIntent().getBooleanExtra(EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageInfo.packageName, null));
        }

        Drawable icon = appInfo.loadIcon(pm);
        CharSequence label = appInfo.loadLabel(pm);
        fragment.setHeader(icon, label, infoIntent);

        ActionBar ab = activity.getActionBar();
        if (ab != null) {
            ab.setTitle(R.string.app_permissions);
        }

        ViewGroup rootView = (ViewGroup) fragment.getView();
        ImageView iconView = (ImageView) rootView.findViewById(R.id.lb_icon);
        if (iconView != null) {
            iconView.setImageDrawable(icon);
        }
        TextView titleView = (TextView) rootView.findViewById(R.id.lb_title);
        if (titleView != null) {
            titleView.setText(R.string.app_permissions);
        }
        TextView breadcrumbView = (TextView) rootView.findViewById(R.id.lb_breadcrumb);
        if (breadcrumbView != null) {
            breadcrumbView.setText(label);
        }
    }

    private void addPermissionOp(PreferenceGroup parent, final Permission permission) {
        if (!permission.hasAppOp()) {
            Log.w(LOG_TAG, "no app opp for permission: " + permission);
            return;
        }
        final int uid = mAppPermissions.getPackageInfo().applicationInfo.uid;
        final String packageName = mAppPermissions.getPackageInfo().packageName;
        final int appOpMode = mAppOps.checkOp(permission.getAppOp(), uid, packageName);

        // strict op's get an on/off switch, others can do always-ask
        if (AppOpsManager.isStrictOp(permission.getAppOp())) {
            SwitchPreference switchPref = new SwitchPreference(getPreferenceManager().getContext());
            switchPref.setChecked(appOpMode == AppOpsManager.MODE_ALLOWED);
            switchPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // on = always ask, off = off
                    mAppOps.setMode(permission.getAppOp(),
                            uid,
                            packageName,
                            newValue == Boolean.TRUE
                                    ? AppOpsManager.MODE_ASK
                                    : AppOpsManager.MODE_IGNORED);

                    loadPreferences();
                    return true;
                }
            });
            switchPref.setPersistent(false);
            switchPref.setKey(permission.getName());
            switchPref.setTitle(getPermissionLabel(permission, false));
            switchPref.setSummary(getPermissionLabel(permission, true));
            switchPref.setIcon(null);

            parent.addPreference(switchPref);
        } else {
            final List<AppOpsManager.PackageOps> opsForPackage = mAppOps.getOpsForPackage(
                    uid, packageName, new int[]{permission.getAppOp()});

            int selectedMode = AppOpsManager.opToDefaultMode(permission.getAppOp(),
                    AppOpsManager.isStrictEnable());

            if (opsForPackage != null) {
                final List<AppOpsManager.OpEntry> ops = opsForPackage.get(0).getOps();
                for (AppOpsManager.OpEntry op : ops) {
                    if (op.getOp() == permission.getAppOp()) {
                        selectedMode = op.getMode();
                        break;
                    }
                }
            }
            ListPreference listPref = new ListPreference(getPreferenceManager().getContext());
            listPref.setKey(permission.getName());
            listPref.setEntries(R.array.app_ops_permissions);
            listPref.setEntryValues(R.array.app_ops_permissions_values);
            listPref.setDefaultValue(String.valueOf(selectedMode));

            listPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Integer newAppOpMode = Integer.parseInt((String) newValue);
                    // on = always ask, off = off
                    mAppOps.setMode(permission.getAppOp(),
                            uid,
                            packageName,
                            newAppOpMode);

                    loadPreferences();
                    return true;
                }
            });

            listPref.setPersistent(false);
            listPref.setIcon(null);
            listPref.setTitle(getPermissionLabel(permission, false));
            listPref.setSummary(getPermissionLabel(permission, true));

            parent.addPreference(listPref);
        }
    }

    private CharSequence getPermissionLabel(Permission permission, boolean summary) {
        final PermissionInfo permissionInfo;
        try {
            permissionInfo = getActivity().getPackageManager()
                    .getPermissionInfo(permission.getName(), 0);
            if (permissionInfo != null) {
                if (summary) {
                    return permissionInfo.loadDescription(getActivity().getPackageManager());
                } else {
                    return permissionInfo.loadLabel(getActivity().getPackageManager());
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadPreferences() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        if (!mAdditionalPageMode) {
            setPreferencesFromResource(R.xml.app_permissions, null);
        }

        PreferenceCategory runtimeCat = (PreferenceCategory) findPreference(RUNTIME_PERMS_CAT);

        int additionalPrefsCount = 0;

        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (!Utils.shouldShowPermission(group, mAppPermissions.getPackageInfo().packageName)) {
                // for groups we shouldn't show, either count them, or add them if we're in the
                // additional page
                for (Permission p : group.getPermissions()) {
                    if (mAdditionalPageMode) {
                        addPermissionOp(getPreferenceScreen(), p);
                    } else if (p.hasAppOp()) {
                        additionalPrefsCount++;
                    }
                }
                continue;
            }

            if (mAdditionalPageMode) {
                // additional page has no runtime permissions to list
                continue;
            }

            SwitchPreference preference = new SwitchPreference(context);
            preference.setOnPreferenceChangeListener(this);
            preference.setKey(group.getName());
            Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                    group.getIconPkg(), group.getIconResId());
            preference.setIcon(Utils.applyTint(getContext(), icon,
                    android.R.attr.colorControlNormal));
            preference.setTitle(group.getLabel());
            if (group.isPolicyFixed()) {
                preference.setSummary(getString(R.string.permission_summary_enforced_by_policy));
            }
            preference.setPersistent(false);
            preference.setEnabled(!group.isPolicyFixed());
            preference.setChecked(!group.isUserSet()
                    ? group.hasGrantedByDefaultPermission() : group.areRuntimePermissionsGranted());

            runtimeCat.addPreference(preference);

            // not checked, list out permissions underneath if there's more than 1
            if (!preference.isChecked()) {
                final List<Permission> permissions = group.getPermissions();
                if (permissions.size() > 1) {
                    for (final Permission permission : permissions) {
                        addPermissionOp(runtimeCat, permission);
                    }
                }
            }
        }

        // add additional permissions link if there are any
        if (additionalPrefsCount > 0 && !mAdditionalPageMode) {
            final Preference extraPerms = new Preference(context);
            extraPerms.setIcon(R.drawable.ic_toc);
            extraPerms.setTitle(R.string.additional_permissions);
            extraPerms.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final AppPermissionsFragment frag =
                            newInstance(getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
                    frag.getArguments().putBoolean(ARG_ADDITIONAL_PAGE, true);
                    getFragmentManager().beginTransaction()
                            .replace(android.R.id.content, frag)
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
            });
            extraPerms.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, additionalPrefsCount, additionalPrefsCount));
            getPreferenceScreen().addPreference(extraPerms);
        }

        setLoading(false /* loading */, true /* animate */);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
        String groupName = preference.getKey();
        final AppPermissionGroup group = mAppPermissions.getPermissionGroup(groupName);

        if (group == null) {
            return false;
        }

        OverlayTouchActivity activity = (OverlayTouchActivity) getActivity();
        if (activity.isObscuredTouch()) {
            activity.showOverlayDialog();
            return false;
        }

        addToggledGroup(group);

        if (LocationUtils.isLocationGroupAndProvider(group.getName(), group.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(), mAppPermissions.getAppLabel());
            return false;
        }
        if (newValue == Boolean.TRUE) {
            group.grantRuntimePermissions(false);
        } else {
            final boolean grantedByDefault = group.hasGrantedByDefaultPermission();
            if (grantedByDefault || (!group.hasRuntimePermission() && !mHasConfirmedRevoke)) {
                new AlertDialog.Builder(getContext())
                        .setMessage(grantedByDefault ? R.string.system_warning
                                : R.string.old_sdk_deny_warning)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.grant_dialog_button_deny,
                                new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SwitchPreference) preference).setChecked(false);
                                group.revokeRuntimePermissions(false);
                                if (!grantedByDefault) {
                                    mHasConfirmedRevoke = true;
                                }
                            }
                        })
                        .show();
                return false;
            } else {
                group.revokeRuntimePermissions(false);
            }
        }
        loadPreferences();
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        logToggledGroups();
    }

    private void addToggledGroup(AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArrayList<>();
        }
        // Double toggle is back to initial state.
        if (mToggledGroups.contains(group)) {
            mToggledGroups.remove(group);
        } else {
            mToggledGroups.add(group);
        }
    }

    private void logToggledGroups() {
        if (mToggledGroups != null) {
            String packageName = mAppPermissions.getPackageInfo().packageName;
            SafetyNetLogger.logPermissionsToggled(packageName, mToggledGroups);
            mToggledGroups = null;
        }
    }

    private void setPreferencesCheckedState() {
        if (!mAdditionalPageMode) {
            setPreferencesCheckedState((PreferenceGroup) findPreference(RUNTIME_PERMS_CAT));
        }
    }

    private void setPreferencesCheckedState(PreferenceGroup screen) {
        int preferenceCount = screen.getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof SwitchPreference) {
                SwitchPreference switchPref = (SwitchPreference) preference;
                AppPermissionGroup group = mAppPermissions.getPermissionGroup(switchPref.getKey());
                if (group != null) {
                    switchPref.setChecked(group.areRuntimePermissionsGranted());
                }
            }
        }
    }

    private static PackageInfo getPackageInfo(Activity activity, String packageName) {
        try {
            return activity.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e);
            return null;
        }
    }
}
