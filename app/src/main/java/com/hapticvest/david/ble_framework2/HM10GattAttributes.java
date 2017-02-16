package com.hapticvest.david.ble_framework2;

import java.util.HashMap;

/**
 * Created by David on 05/12/2016.
 */

public class HM10GattAttributes {
    private static HashMap<String, String> attributes = new HashMap<String, String>();
    public final static String HM_10_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public final static String HM_10_CHARAC = "0000ffe1-0000-1000-8000-00805f9b34fb";

    static {
        // RBL Services.
        attributes.put(HM_10_SERVICE, "HM-10 Service");
        // RBL Characteristics.
        attributes.put(HM_10_CHARAC, "HM-10 Characteristic");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
