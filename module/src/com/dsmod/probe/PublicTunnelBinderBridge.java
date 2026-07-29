package com.dsmod.probe;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * UID-authenticated Binder endpoint delivered through the existing browsable trampoline.
 *
 * <p>Android package visibility can hide a module provider or service from an unmodified host.
 * A framework {@link android.os.ResultReceiver} lets the trampoline return this Binder without
 * putting credentials in a URI. Every transaction then verifies the real Binder caller UID
 * against {@code com.deepseek.chat}; merely obtaining the Binder grants no access.</p>
 */
final class PublicTunnelBinderBridge {
    static final String DESCRIPTOR = "com.dsmod.probe.IPublicTunnelBridge";
    static final int TRANSACTION_STATUS = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_CONFIGURE = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_SET_REQUESTED = IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_PINGGY_STATUS = IBinder.FIRST_CALL_TRANSACTION + 3;
    static final int TRANSACTION_SET_PINGGY_REQUESTED = IBinder.FIRST_CALL_TRANSACTION + 4;

    private static final String TARGET_PACKAGE = "com.deepseek.chat";
    private static final String TAG = "DeekseepTunnelBridge";
    private static final IBinder BRIDGE = new Bridge();
    private static volatile Context applicationContext;

    private PublicTunnelBinderBridge() {}

    static IBinder binder(Context context) {
        if (context != null) applicationContext = context.getApplicationContext();
        return BRIDGE;
    }

    private static final class Bridge extends Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code != TRANSACTION_STATUS && code != TRANSACTION_CONFIGURE
                    && code != TRANSACTION_SET_REQUESTED
                    && code != TRANSACTION_PINGGY_STATUS
                    && code != TRANSACTION_SET_PINGGY_REQUESTED) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(DESCRIPTOR);
            Bundle extras = data.readBundle(PublicTunnelBinderBridge.class.getClassLoader());
            Bundle result;
            Context context = applicationContext;
            int callingUid = Binder.getCallingUid();
            if (context == null) {
                result = new Bundle();
                result.putBoolean("accepted", false);
                result.putString("error", "module context is unavailable");
            } else if (!uidOwnsPackage(context, callingUid, TARGET_PACKAGE)) {
                Log.w(TAG, "rejected public tunnel transaction from uid=" + callingUid);
                result = new Bundle();
                result.putBoolean("accepted", false);
                result.putString("error", "caller is not DeepSeek");
            } else if (code == TRANSACTION_CONFIGURE) {
                result = PublicTunnelManager.configure(context, extras);
            } else if (code == TRANSACTION_SET_REQUESTED) {
                result = PublicTunnelManager.setRequested(
                        context, extras != null && extras.getBoolean("enabled", false));
            } else if (code == TRANSACTION_SET_PINGGY_REQUESTED) {
                result = PinggyTunnelManager.setRequested(
                        context, extras != null && extras.getBoolean("enabled", false));
            } else if (code == TRANSACTION_PINGGY_STATUS) {
                result = PinggyTunnelManager.status(context);
            } else {
                result = PublicTunnelManager.status(context);
            }
            reply.writeNoException();
            reply.writeBundle(result);
            return true;
        }
    }

    private static boolean uidOwnsPackage(Context context, int uid, String wanted) {
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (String name : packages) {
                if (wanted.equals(name)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
