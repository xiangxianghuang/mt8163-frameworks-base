package com.mediatek.systemui.ext;

import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * M: the interface for Plug-in definition of Status bar.
 */
public interface ISystemUIStatusBarExt {

    /**
     * Get the current service state for op customized view update.
     * @param subId the sub id of SIM.
     */
    void getServiceStateForCustomizedView(int subId);

    /**
     * Get the customized network type icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original network type icon id.
     * @param networkType the network type.
     * @param serviceState the service state.
     * @return the customized network type icon id.
     */
    int getNetworkTypeIcon(int subId, int iconId, int networkType,
            ServiceState serviceState);

    /**
     * Get the customized data type icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original data type icon id.
     * @param dataType the data connection type.
     * @param dataState the data connection state.
     * @param serviceState the service state.
     * @return the customized data type icon id.
     */
    int getDataTypeIcon(int subId, int iconId, int dataType, int dataState,
            ServiceState serviceState);

    /**
     * Get the customized signal strength icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original signal strength icon id.
     * @param signalStrength the signal strength.
     * @param networkType the network type.
     * @param serviceState the service state.
     * @return the customized signal strength icon id.
     */
    int getCustomizeSignalStrengthIcon(int subId, int iconId,
            SignalStrength signalStrength, int networkType,
            ServiceState serviceState);

    /**
     * Add the customized view.
     * @param subId the sub id of SIM.
     * @param context the context.
     * @param root the root view group in which the customized view
     *             will be added.
     */
    void addCustomizedView(int subId, Context context, ViewGroup root);

    /**
     * Set the customized network type view.
     * @param subId the sub id of SIM.
     * @param networkTypeId the customized network type icon id.
     * @param networkTypeView the network type view
     *                        which needs to be customized.
     */
    void setCustomizedNetworkTypeView(int subId,
            int networkTypeId, ImageView networkTypeView);

    /**
     * Set the customized data type view.
     * @param subId the sub id of SIM.
     * @param dataTypeId the customized data type icon id.
     * @param dataIn the data in state.
     * @param dataOut the data out state.
     */
    void setCustomizedDataTypeView(int subId,
            int dataTypeId, boolean dataIn, boolean dataOut);

    /**
     * Set the customized mobile type view.
     * @param subId the sub id of SIM.
     * @param mobileTypeView the mobile type view which needs to be customized.
     */
    void setCustomizedMobileTypeView(int subId, ImageView mobileTypeView);

    /**
     * Set the customized signal strength view.
     * @param subId the sub id of SIM.
     * @param signalStrengthId the customized signal strength icon id.
     * @param signalStrengthView the signal strength view
     *                           which needs to be customized.
     */
    void setCustomizedSignalStrengthView(int subId,
            int signalStrengthId, ImageView signalStrengthView);

    /**
     * Set the other customized views.
     * @param subId the sub id of SIM.
     */
    void setCustomizedView(int subId);

    /**
     * Set the customized no sim view.
     * @param noSimView the no sim view which needs to be customized.
     */
    void setCustomizedNoSimView(ImageView noSimView);

    /**
     * Set the customized volte view.
     * @param iconId the original volte icon id.
     * @param volteView the volte view which needs to be customized.
     */
    void setCustomizedVolteView(int iconId, ImageView volteView);

    /**
     * Set the customized no sim and airplane mode view.
     * @param noSimView the no sim view which needs to be customized.
     * @param airplaneMode the airplane mode.
     */
    void setCustomizedAirplaneView(View noSimView, boolean airplaneMode);
}
