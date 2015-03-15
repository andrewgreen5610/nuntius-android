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

package org.holylobster.nuntius;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.JsonWriter;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class Message {
    // Used for logging
    private String TAG = this.getClass().getSimpleName();

    private String event;
    private StatusBarNotification[] notifications;

    public Message(String event, StatusBarNotification... notifications) {
        this.notifications = notifications;
        this.event = event;
    }

    String toJSON(Context context) {
        StringWriter out = new StringWriter();
        JsonWriter writer = new JsonWriter(out);

        try {
            writer.beginObject();
            writer.name("event").value(event);

            writer.name("eventItems");
            writer.beginArray();
            for (StatusBarNotification sbn : notifications) {
                toJSON(context, writer, sbn);
            }
            writer.endArray();
            writer.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return out.toString();
    }

    private void toJSON(Context context, JsonWriter writer, StatusBarNotification sbn) throws IOException {
        writer.beginObject();

        writer.name("id").value(sbn.getId());
        writer.name("packageName").value(sbn.getPackageName());
        writer.name("clearable").value(sbn.isClearable());
        writer.name("ongoing").value(sbn.isOngoing());
        writer.name("postTime").value(sbn.getPostTime());
        String tag = sbn.getTag();
        if (tag != null) {
            writer.name("tag").value(tag);
        }

        try {
            BitmapDrawable icon = (BitmapDrawable) context.getPackageManager().getApplicationIcon(sbn.getPackageName());
            Bitmap bitmap = icon.getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            writer.name("icon").value(new String(Base64.encode(stream.toByteArray(), Base64.DEFAULT), "UTF-8"));
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Could not get the icon from the notification: " + sbn.getPackageName());
        }

        writePropertiesLollipop(writer, sbn);

        writer.name("notification");
        Notification notification = sbn.getNotification();

        writer.beginObject();
        writer.name("priority").value(notification.priority);
        writer.name("when").value(notification.when);
        writer.name("defaults").value(notification.defaults);
        writer.name("flags").value(notification.flags);
        writer.name("number").value(notification.number);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle extras = notification.extras;

            CharSequence notificationTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (notificationTitle != null) {
                writer.name("title").value(notificationTitle.toString());
            }

            CharSequence notificationText = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (notificationText != null) {
                writer.name("text").value(notificationText.toString());
            }

            CharSequence notificationSubText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            if (notificationSubText != null) {
                writer.name("subText").value(notificationSubText.toString());
            }
        }

        CharSequence tickerText = notification.tickerText;
        if (tickerText != null) {
            writer.name("tickerText").value(tickerText.toString());
        }

        PendingIntent contentIntent = notification.contentIntent;
        PendingIntent deleteIntent = notification.deleteIntent;
        PendingIntent fullScreenIntent = notification.fullScreenIntent;
        if (contentIntent != null) {
            writer.name("contentIntent").value(contentIntent.toString());
        }

        if (deleteIntent != null) {
            writer.name("deleteIntent").value(deleteIntent.toString());
        }

        if (fullScreenIntent != null) {
            writer.name("fullScreenIntent").value(fullScreenIntent.toString());
        }

        writeNotificationLollipop(writer, notification);

        writeActions(writer, notification);

        writer.endObject();
        writer.endObject();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void writePropertiesLollipop(JsonWriter writer, StatusBarNotification sbn) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        writer.name("key").value(sbn.getKey());
        writer.name("groupKey").value(sbn.getGroupKey());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void writeNotificationLollipop(JsonWriter writer, Notification notification) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        String category = notification.category;
        if (category != null) {
            writer.name("category").value(category);
        }
        writer.name("color").value(notification.color);
        writer.name("visibility").value(notification.visibility);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void writeActions(JsonWriter writer, Notification notification) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }

        writer.name("actions");
        writer.beginArray();

        if (notification.actions != null) {
            for (Notification.Action a : notification.actions) {
                writer.beginObject();
                writer.name("title").value(a.title.toString());
                writer.endObject();
            }
        }

        writeWearableActions(writer, notification);

        writer.endArray();
    }

    private void writeWearableActions(JsonWriter writer, Notification notification) throws IOException {
        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender(notification);
        List<NotificationCompat.Action> wearableExtenderActions = wearableExtender.getActions();

        for (final NotificationCompat.Action action : wearableExtenderActions) {
            Log.i(TAG, "Wearable Action Title: " + action.title);
            Log.i(TAG, "-- Extras: " + action.getExtras());
            Log.i(TAG, "-- Intent: " + action.actionIntent);

            writer.beginObject();
            writer.name("title").value(action.title.toString());

            android.support.v4.app.RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs != null) {
                writer.name("remoteInputs");
                writer.beginArray();

                for (android.support.v4.app.RemoteInput remoteInput : remoteInputs) {
                    writer.beginObject();
                    writer.name("label").value(remoteInput.getLabel().toString());
                    writer.name("allowFreeFormInput").value(remoteInput.getAllowFreeFormInput());

                    Log.i(TAG, "-- Remote Input: " + remoteInput.toString());
                    Log.i(TAG, "----- Label: " + remoteInput.getLabel());
                    CharSequence[] choices = remoteInput.getChoices();
                    if (choices != null) {
                        writer.name("choices");
                        writer.beginArray();

                        for (CharSequence choice : choices) {
                            Log.i(TAG, "----- Choice: " + choice.toString());
                            writer.value(choice.toString());
                        }

                        writer.endArray();
                    }
                    Log.i(TAG, "----- Extras: " + remoteInput.getExtras());
                    Log.i(TAG, "----- Freeform: " + remoteInput.getAllowFreeFormInput());

                    writer.endObject();
                }

                writer.endArray();
            }

            writer.endObject();
        }
    }

}
