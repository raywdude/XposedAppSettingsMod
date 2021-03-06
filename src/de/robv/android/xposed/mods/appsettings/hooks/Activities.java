package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;

public class Activities {
	@SuppressLint("InlinedApi")
	private static final int IMMERSIVE_SYSTEM_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";
	private static final String PROP_FS_IMMERSIVE = "AppSettings-FS-Immersive";

	public static void hookActivitySettings() {
		try {
			findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", null, "generateLayout",
					"com.android.internal.policy.impl.PhoneWindow.DecorView", new XC_MethodHook() {

				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Window window = (Window) param.thisObject;
					Context context = window.getContext();
					String packageName = context.getPackageName();

					if (!XposedMod.isActive(packageName))
						return;

					int fullscreen;
					try {
						fullscreen = XposedMod.prefs.getInt(packageName + Common.PREF_FULLSCREEN,
								Common.FULLSCREEN_DEFAULT);
					} catch (ClassCastException ex) {
						// Legacy boolean setting
						fullscreen = XposedMod.prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false)
								? Common.FULLSCREEN_FORCE : Common.FULLSCREEN_DEFAULT;
					}
					if (fullscreen == Common.FULLSCREEN_FORCE) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
					} else if (fullscreen == Common.FULLSCREEN_PREVENT) {
						window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.FALSE);
					} else if (fullscreen == Common.FULLSCREEN_IMMERSIVE && context instanceof Activity) {
						setAdditionalInstanceField(context, PROP_FS_IMMERSIVE, Boolean.TRUE);
					}

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_NO_TITLE, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);
					
					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_ALLOW_ON_LOCKSCREEN, false))
		    				window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
		    				    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
		    				    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_SCREEN_ON, false)) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						setAdditionalInstanceField(window, PROP_KEEP_SCREEN_ON, Boolean.TRUE);
					}

					int orientation = XposedMod.prefs.getInt(packageName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Common.orientationCodes.length && context instanceof Activity) {
						((Activity) context).setRequestedOrientation(Common.orientationCodes[orientation]);
						setAdditionalInstanceField(context, PROP_ORIENTATION, orientation);
					}
					
					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_TRANS_NAVBAR, false)) {
						setNavbarTransparent(window);
					}
				}
			});
			
			if (Build.VERSION.SDK_INT >= 19) {
				findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Activity activity = (Activity) param.thisObject;
						
						Boolean bForceImmersive = (Boolean) getAdditionalInstanceField(activity, PROP_FS_IMMERSIVE);
						if (bForceImmersive != null && bForceImmersive) {
							setImmersive(activity);
						}
					}
				});

				// Some applications like Dolphin Browser needs re-setting the immersive flag
				//	once in a while... because they reset the window flags.
				findAndHookMethod(Activity.class, "onUserInteraction", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					    super.afterHookedMethod(param);
					    Activity activity = (Activity)param.thisObject;
					    
						Boolean bForceImmersive = (Boolean) getAdditionalInstanceField(activity, PROP_FS_IMMERSIVE);
						if (bForceImmersive != null && bForceImmersive) {			
								setImmersive(activity);
						}
					}
				});
			}
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
		
		try {
			findAndHookMethod(Window.class, "setFlags", int.class, int.class,
					new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

					int flags = (Integer) param.args[0];
					int mask = (Integer) param.args[1];
					if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
						Boolean fullscreen = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_FULLSCREEN);
						if (fullscreen != null) {
							if (fullscreen.booleanValue()) {
								flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
							} else {
								flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
							}
							param.args[0] = flags;
						}
					}
					if ((mask & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
						Boolean keepScreenOn = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_KEEP_SCREEN_ON);
						if (keepScreenOn != null) {
							if (keepScreenOn.booleanValue()) {
								flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
							}
							param.args[0] = flags;
						}
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Integer orientation = (Integer) getAdditionalInstanceField(param.thisObject, PROP_ORIENTATION);
					if (orientation != null)
						param.args[0] = Common.orientationCodes[orientation];
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
			Method mthRealStartActivityLocked;
			if (Build.VERSION.SDK_INT <= 18) {
				try {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class, boolean.class);
				} catch (NoSuchMethodError t) {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class);
				}
			} else {
				mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStackSupervisor", null, "realStartActivityLocked",
						"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
						boolean.class, boolean.class);
			}
			hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {

	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	    			String pkgName = (String) getObjectField(param.args[0], "packageName");
	    			if (!XposedMod.isActive(pkgName, Common.PREF_RESIDENT))
	    				return;
	    			
					int adj = -12;
					Object proc = getObjectField(param.args[0], "app");
					
					// Override the *Adj values if meant to be resident in memory
					if (proc != null) {
						setIntField(proc, "maxAdj", adj);
						if (Build.VERSION.SDK_INT <= 18)
							setIntField(proc, "hiddenAdj", adj);
						setIntField(proc, "curRawAdj", adj);
						setIntField(proc, "setRawAdj", adj);
						setIntField(proc, "curAdj", adj);
						setIntField(proc, "setAdj", adj);
					}
	    		}
	    	});
	    } catch (Throwable e) {
	        XposedBridge.log(e);
	    }

		try {
			findAndHookMethod(InputMethodService.class, "doStartInput",
					InputConnection.class, EditorInfo.class, boolean.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					EditorInfo info = (EditorInfo) param.args[1];
					if (info != null && info.packageName != null) {
						if (XposedMod.isActive(info.packageName, Common.PREF_NO_FULLSCREEN_IME))
							info.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
    }
	
	@SuppressLint({ "InlinedApi", "NewApi" })
	private static void setImmersive(Activity activity) {
		if (activity == null) {
			return;
		}
		activity.setImmersive(true);
		Window window = activity.getWindow();
		if (window != null) {
			window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			View decorView = window.getDecorView();
			if (decorView != null) {
				decorView.setSystemUiVisibility(IMMERSIVE_SYSTEM_UI_FLAGS);
			}
		}
	}
	
	@SuppressLint("InlinedApi")
	private static void setNavbarTransparent(Window window) {
		if (window != null) {
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
			window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		}
	}
}
