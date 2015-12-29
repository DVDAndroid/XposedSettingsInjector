package com.arilotter.xposedsettingsinjector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class XposedMod implements IXposedHookLoadPackage {
    @SuppressWarnings("unused")
    private static final String TAG = "InjectXposedPreference";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.settings"))
            return;
        final Class<?> DashboardCategory = findClass("com.android.settings.dashboard.DashboardCategory", lpparam.classLoader);
        Class<?> SettingsActivity = findClass("com.android.settings.SettingsActivity", lpparam.classLoader);
        final Class<?> DashboardTile = findClass("com.android.settings.dashboard.DashboardTile", lpparam.classLoader);

        // TODO find a way to disable tint?
//        findAndHookMethod(DashboardSummary, "updateTileView", Context.class, Resources.class, DashboardTile, ImageView.class, TextView.class, TextView.class, new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                ImageView view = ((ImageView) param.args[4]);
//                Drawable icon = view.getDrawable();
//                icon.setTintList(null);
//            }
//        });

        findAndHookMethod(SettingsActivity, "loadCategoriesFromResource", "int", List.class, Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[2];
                PackageManager pm = context.getPackageManager();
                ArrayList<ApplicationInfo> xposedModulesList = new ArrayList<>();
                List<String> activeModules = ModuleLoader.getActiveModules();

                for (String s : activeModules) {
                    // example
                    // s = "/data/app/com.arilotter.xposedsettingsinjector-2/base.apk"

                    s = s.replace("/data/app/", ""); // == /data/app/com.arilotter.xposedsettingsinjector-2/base.apk
                    s = s.replace("/base.apk", ""); // == com.arilotter.xposedsettingsinjector-2
                    s = s.substring(0, s.length() - 2); // == com.arilotter.xposedsettingsinjector

                    ApplicationInfo app = context.getPackageManager().getApplicationInfo(s, PackageManager.GET_META_DATA);

                    if (app.metaData != null && app.metaData.containsKey("xposedmodule"))
                        xposedModulesList.add(app);
                }

                ApplicationInfo xposedInstaller;
                try {
                    xposedInstaller = context.getPackageManager().getApplicationInfo("de.robv.android.xposed.installer", 0);

                    xposedModulesList.add(xposedInstaller);
                } catch (PackageManager.NameNotFoundException ignored) {}

                Collections.sort(xposedModulesList, new ApplicationInfo.DisplayNameComparator(pm));

                Object category = DashboardCategory.newInstance();
                setObjectField(category, "title", "Xposed");

                for (ApplicationInfo info : xposedModulesList) {
                    Intent intent = ModuleLoader.getSettingsIntent(pm, info.packageName);
                    if (intent != null) {
                        Object tile = newInstance(DashboardTile);
                        setObjectField(tile, "title", pm.getApplicationLabel(info));
                        setIntField(tile, "iconRes", info.icon);
                        setObjectField(tile, "iconPkg", info.packageName);
//                    tile.icon = pm.getApplicationIcon(info);
                        setObjectField(tile, "intent", intent);
                        callMethod(category, "addTile", tile);
                    }
                }
                // Add our category to the list of categories
                ((List) param.args[1]).add(category);
            }
        });
    }
}