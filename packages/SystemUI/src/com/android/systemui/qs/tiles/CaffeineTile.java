/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.SystemUIDialog;

/** Quick settings tile: Caffeine **/
public class CaffeineTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_caffeine_on);

    private final PowerManager.WakeLock mWakeLock;
    private final Receiver mReceiver = new Receiver();

    public CaffeineTile(QSHost host) {
        super(host);
        mWakeLock = ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.FULL_WAKE_LOCK, "CaffeineTile");
        mReceiver.init();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mReceiver.destroy();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_caffeine_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.NITROGEN_SETTINGS;
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public void handleClick() {
        if (Prefs.getBoolean(mContext, Prefs.Key.QS_CAFFEINE_DIALOG_SHOWN, false)) {
            drinkUp();
            return;
        }
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(R.string.caffeine_info_title);
        dialog.setMessage(R.string.caffeine_info_message);
        dialog.setPositiveButton(com.android.internal.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        drinkUp();
                        Prefs.putBoolean(mContext, Prefs.Key.QS_CAFFEINE_DIALOG_SHOWN, true);
                    }
                });
        dialog.setShowForAllUsers(true);
        dialog.show();
    }

    public void drinkUp() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        } else {
            mWakeLock.acquire();
        }
        refreshState();
    }

    @Override
    public void handleLongClick() {
        setCpuInfoEnabled();
    }

    @Override
    public Intent getLongClickIntent() {
        return  null;
    }

    private boolean setCpuInfoEnabled() {
        boolean enabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, 1) != 0;
        Intent service = (new Intent())
                .setClassName("com.android.systemui",
                "com.android.systemui.CPUInfoService");
        if (!enabled) {
            Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.SHOW_CPU_OVERLAY, 1);
            mContext.startService(service);
        } else {
            Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.SHOW_CPU_OVERLAY, 0);
            mContext.stopService(service);
        }
        return enabled;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.icon = mIcon;
        state.value = mWakeLock.isHeld();
        state.label = mContext.getString(R.string.quick_settings_caffeine_label);
        if (state.value) {
            state.slash.isSlashed = false;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.slash.isSlashed = true;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    private final class Receiver extends BroadcastReceiver {
        public void init() {
            // Register for Intent broadcasts for...
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        public void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // disable caffeine if user force off (power button)
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                refreshState();
            }
        }
    }
}
