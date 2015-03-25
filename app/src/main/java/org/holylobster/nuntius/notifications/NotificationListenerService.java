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

package org.holylobster.nuntius.notifications;

import android.content.Intent;
import android.os.IBinder;
import android.security.KeyPairGeneratorSpec;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.holylobster.nuntius.Server;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.x509.X509V3CertificateGenerator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

import javax.security.auth.x500.X500Principal;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {

    private final String TAG = this.getClass().getSimpleName();

    private static boolean isNotificationAccessEnabled = false;

    public static Server server;

    @Override
    public IBinder onBind(Intent mIntent) {
        IBinder mIBinder = super.onBind(mIntent);
        Log.i(TAG, "onBind");
        isNotificationAccessEnabled = true;
        try {
            server = new Server(this);
            server.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return mIBinder;
    }

    @Override
    public boolean onUnbind(Intent mIntent) {
        boolean mOnUnbind = super.onUnbind(mIntent);
        Log.i(TAG, "onUnbind");
        isNotificationAccessEnabled = false;
        try {
            server.stop();
            server = null;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return mOnUnbind;
    }

    public static boolean isNotificationAccessEnabled() {
        return isNotificationAccessEnabled;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "Notification from " + sbn.getPackageName() + ", prio=" + sbn.getNotification().priority);
        server.onNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "Notification from " + sbn.getPackageName() + " removed");
        server.onNotificationRemoved(sbn);
    }

}
