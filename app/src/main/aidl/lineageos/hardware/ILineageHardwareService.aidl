/*
 * SPDX-License-Identifier: Apache-2.0
 * Mirrors LineageOS android_lineage-sdk (lineage-24.0):
 * sdk/src/java/lineageos/hardware/ILineageHardwareService.aidl
 */

package lineageos.hardware;

/** @hide */
interface ILineageHardwareService {
    int getSupportedFeatures();
    boolean get(int feature);
    boolean set(int feature, boolean enable);

    int[] getDisplayColorCalibration();
    boolean setDisplayColorCalibration(in int[] rgb);
}
