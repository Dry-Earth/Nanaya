package io.github.dryearth.solidbutler.proxy;

import io.github.dryearth.solidbutler.proxy.INanayaProxyCallback;

interface INanayaProxy {
    int getVersion();
    int getUid();
    boolean checkPermission(String permission);
    void requestPermission(String permission, int requestCode, INanayaProxyCallback callback);
    void revokePermission(String permission);
}
