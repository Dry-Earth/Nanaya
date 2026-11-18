/*
 * SPDX-License-Identifier: Apache-2.0
 * Mirrors LineageOS android_lineage-sdk (lineage-24.0):
 * sdk/src/java/lineageos/health/IHealthInterface.aidl
 */

package lineageos.health;

/** @hide */
interface IHealthInterface {
    boolean isChargingControlSupported();

    boolean getChargingControlEnabled();
    boolean setChargingControlEnabled(boolean enabled);

    int getChargingControlMode();
    boolean setChargingControlMode(int mode);

    int getChargingControlStartTime();
    boolean setChargingControlStartTime(int time);

    int getChargingControlTargetTime();
    boolean setChargingControlTargetTime(int time);

    int getChargingControlLimit();
    boolean setChargingControlLimit(int limit);

    boolean resetChargingControl();
    boolean allowFineGrainedSettings();

    boolean isFastChargeSupported();
    int[] getSupportedFastChargeModes();
    int getFastChargeMode();
    boolean setFastChargeMode(int mode);
}
