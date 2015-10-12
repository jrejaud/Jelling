package com.github.jrejaud.jelling;

import android.app.Application;
import android.test.ApplicationTestCase;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    public ApplicationTest() {
        super(Application.class);
    }

    private void testJelling() {

        String myDeviceName = "myTestDevice";
        String myDeviceUUID = "1234";

        Jelling jelling = Jelling.getInstance();
        jelling.setupBluetooth(getContext());
        jelling.connectToDeviceByNameAndUUID(myDeviceName,myDeviceUUID);
        jelling.sendMessage("This is a message!");
    }
}