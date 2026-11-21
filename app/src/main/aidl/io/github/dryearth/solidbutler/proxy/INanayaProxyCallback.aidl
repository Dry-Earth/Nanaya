package io.github.dryearth.solidbutler.proxy;

oneway interface INanayaProxyCallback {
    void onPermissionResult(int requestCode, boolean granted, boolean oneTime);
}
