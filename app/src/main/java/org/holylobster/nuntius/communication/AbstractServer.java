/*
 * Copyright (C) 2015 - Holy Lobster
 *
 * Nuntius is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Nuntius is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Nuntius. If not, see <http://www.gnu.org/licenses/>.
 */

package org.holylobster.nuntius.communication;

import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.holylobster.nuntius.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractServer extends BroadcastReceiver implements Server, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = AbstractServer.class.getSimpleName();

    private final List<Connection> connections = new ArrayList<>();
    private Thread acceptThread;

    private int minNotificationPriority = Notification.PRIORITY_DEFAULT;

    final Context context;

    AbstractServer(Context context) {
        this.context = context;
    }

    public void onNotificationPosted(StatusBarNotification sbn) {
        if (filter(sbn)) {
            sendMessage("notificationPosted", sbn);
        }
    }

    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (filter(sbn)) {
            sendMessage("notificationRemoved", sbn);
        }
    }

    private boolean filter(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        return
                notification != null
                // Filter low priority notifications
                && notification.priority >= minNotificationPriority
                // Notification flags
                && !isOngoing(notification)
                && !isLocalOnly(notification);
    }

    private static boolean isLocalOnly(Notification notification) {
        boolean local = (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0;
        Log.d(TAG, String.format("Notification is local: %1s", local));
        return local;

    }

    void addSocket(NuntiusSocket socket) {
        connections.add(new Connection(context, socket));
    }

    abstract NuntiusSocket accept() throws IOException;

    boolean startupChecks() {
        return true;
    }

    private static boolean isOngoing(Notification notification) {
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        Log.d(TAG, String.format("Notification is ongoing: %1s", ongoing));
        return ongoing;
    }

    private void sendMessage(String event, StatusBarNotification sbn) {
        for (Connection connection : connections) {
            boolean queued = connection.enqueue(new Message(event, sbn));
            if (!queued) {
                Log.w(TAG, "Unable to enqueue message on connection " + connection);
            }
        }
    }

    boolean isAlive() {
        return acceptThread != null && acceptThread.isAlive();
    }

    abstract void setupServerSocket() throws IOException;

    abstract boolean periodicCheck();

    void startThread() {
        if (!startupChecks()) return;

        acceptThread = new Thread() {
            public void run() {
                Log.d(TAG, "Listen server started");

                try {
                    setupServerSocket();

                    while (periodicCheck()) {
                        try {
                            addSocket(accept());
                        } catch (IOException e) {
                            Log.e(TAG, "Error during accept", e);
                            Log.i(TAG, "Waiting 5 seconds before accepting again...");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e1) {
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error in listenUsingRfcommWithServiceRecord", e);
                }
            }
        };
        acceptThread.start();
    }

    private void stopThread() {
        Log.i(TAG, "Stopping server thread.");
        if (acceptThread != null) {
            acceptThread.interrupt();
            for (Connection connection : connections) {
                connection.close();
            }
            connections.clear();
            Log.i(TAG, "Server thread stopped.");
        } else {
            Log.i(TAG, "Server thread already stopped.");
        }

        shutdownServerSocket();

    }

    abstract void shutdownServerSocket();

    public void stop() {
        Log.d(TAG, "Server stopping...");

        context.unregisterReceiver(this);

        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);

        stopThread();
    }

    public void start() {
        Log.d(TAG, "Server starting...");

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(this, filter);

        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        boolean mustRun = defaultSharedPreferences.getBoolean("main_enable_switch", true);

        if (mustRun) {
            startThread();
        }
    }

    public int getNumberOfConnections() {
        return connections.size();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "main_enable_switch":
                if (sharedPreferences.getBoolean("main_enable_switch", true)) {
                    startThread();
                } else {
                    stopThread();
                }
                break;
            case "pref_min_notification_priority":
                minNotificationPriority = Integer.parseInt(sharedPreferences.getString("pref_min_notification_priority", String.valueOf(Notification.PRIORITY_DEFAULT)));
                break;
            default:
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_TURNING_ON:
                    stopThread();
                    break;
                case BluetoothAdapter.STATE_ON:
                    startThread();
                    break;
            }
        }
    }
}