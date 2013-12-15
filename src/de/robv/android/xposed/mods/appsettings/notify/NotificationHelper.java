package de.robv.android.xposed.mods.appsettings.notify;

import de.robv.android.xposed.mods.appsettings.receivers.NotifyClickReceiver;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.NotificationCompat;

public class NotificationHelper {
	private static final String TOGGLE_IMMERSIVE_MODE_TITLE = "Toggle immersive mode";
	private static final String TOGGLE_IMMERSIVE_MODE_FILTER = "toggle_immersive_mode_filter";
	private static final int NOTIFICATION_ID = 133737;
	private static NotificationManager mNotifyMgr;
	private static Activity IMMERSIVE_TARGET_ACTIVITY;
	private static boolean IS_IN_IMMERSIVE_MODE;
	private static boolean IS_IMMERSIVE_TOGGLED;
	private static BroadcastReceiver mReceiver;
	
	public static void showNotification(Context context, Activity activity) {
		if (mNotifyMgr == null) {
			mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		}

		IMMERSIVE_TARGET_ACTIVITY = activity;
		
		if (mReceiver == null) {
			mReceiver = new NotifyClickReceiver();
			context.registerReceiver(mReceiver, new IntentFilter(TOGGLE_IMMERSIVE_MODE_FILTER));
		}
		
		Intent actionIntent = new Intent(TOGGLE_IMMERSIVE_MODE_FILTER);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(context);
		notifyBuilder.setContentIntent(pendingIntent);
		notifyBuilder.setOngoing(true);
		notifyBuilder.setSmallIcon(android.R.drawable.ic_menu_compass);
		notifyBuilder.setContentTitle(TOGGLE_IMMERSIVE_MODE_TITLE);
		
		mNotifyMgr.notify(NOTIFICATION_ID, notifyBuilder.build());
	}
	
	public static boolean hasUserToggledImmersive() {
		return IS_IMMERSIVE_TOGGLED;
	}
	
	public static void setUserToggledImmersive(boolean isSet) {
		IS_IMMERSIVE_TOGGLED = isSet;
	}
	
	public static boolean isImmersive() {
		return IS_IN_IMMERSIVE_MODE;
	}
	
	public static void setImmersive(boolean isSet) {
		IS_IN_IMMERSIVE_MODE = isSet;
	}
	
	public static void restartActivity() {
		if (IMMERSIVE_TARGET_ACTIVITY != null) {
			IS_IMMERSIVE_TOGGLED = true;
			IS_IN_IMMERSIVE_MODE = !IS_IN_IMMERSIVE_MODE;
			IMMERSIVE_TARGET_ACTIVITY.recreate();
		}
	}
	
	public static void dismissNotifications(Context context) {
		if (mNotifyMgr == null) {
			mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		}
		
		if (mReceiver != null) {
			context.unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		
		mNotifyMgr.cancel(NOTIFICATION_ID);
		IMMERSIVE_TARGET_ACTIVITY = null;
		
		if (!IS_IMMERSIVE_TOGGLED) {
			setImmersive(false);
		}
	}
}
