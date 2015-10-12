# jelling
Wrapper for using Bluetooth on Android

###Usage
     String myDeviceName = "myTestDevice"; 
     String myDeviceUUID = "1234";
     
     Jelling jelling = Jelling.getInstance();
     jelling.setupBluetooth(getContext());
     jelling.connectToDeviceByNameAndUUID(myDeviceName,myDeviceUUID);
     jelling.sendMessage("This is a message!");
