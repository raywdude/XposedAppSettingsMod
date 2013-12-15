package de.robv.android.xposed.mods.appsettings.receivers;

import de.robv.android.xposed.mods.appsettings.notify.NotificationHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotifyClickReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		NotificationHelper.restartActivity();
	}
}
