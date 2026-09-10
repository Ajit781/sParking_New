package com.innovus.sparkingnew.printer;

import android.util.Log;

public class BaseTester {

    private String childName = "";

    public BaseTester() {
    }

    public void logTrue(String method) {
        childName = getClass().getSimpleName() + ".";
        String trueLog = childName + method;
        Log.i("PrinterTest", trueLog);
    }

    public void logErr(String method, String errString) {
        childName = getClass().getSimpleName() + ".";
        String errorLog = childName + method + "   errorMessage: " + errString;
        Log.e("PrinterTest", errorLog);
    }
}
