#FStep

**FStep** is a simple demo for the new Android 4.4 step counter API, and for batched sensors data.

**FStep requires a device with the appropriate hardware sensors support (such as the Nexus 5) to be
able to run!**


## What Google is not telling you
As of the time of writing this app, there's nearly no documentation or sample for these new APIs.

We hope that Google eventually fills this gap, but in the meantime, here's a few things you might
want to know...

### Declaring sensors requirement in the manifest

If your app requires support for the step counter sensor, you will have to declare it in the app's
`AndroidManifest.xml` file:

```xml
<uses-feature android:name="android.hardware.sensor.stepcounter" android:required="true"/>
```

Please notice that support for the step counter is set as `required`. In case you wanted to require
the step detector sensor instead (which we don't use in this app) you should replace the
`android:name="android.hardware.sensor.stepcounter"` attribute with the correct one, that is
`android:name="android.hardware.sensor.stepdetector"`.

<hr>
**PROTIP**

If you need to know exactly which features a physical device declares it has, you can
simply connect it to a computer and issue this command:

```
$ adb shell pm list features
```

This was, by the way, the method we've used to determine the feature names for the new step sensors,
since there's no reference to them at all on the Android Developers website.
<hr>

### Registering for the sensor events

By default, Android keeps sensors off unless there is at least one registered listener, in order to save power. In order to get the sensors events, you have to register your activity (or service) with the `SensorManager`:

```java
// Retrieve the sensor manager and the step counter
SensorManager mgr = (SensorManager) getSystemService(SENSOR_SERVICE);
Sensor stepCtr = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

// Register for sensor events
mgr.registerListener(this, stepCtr, SensorManager.SENSOR_DELAY_NORMAL);
```

This is the most basic form of registration for a sensor, and it's basically the same for all sensors on a device.

You must remember to unregister your listener when you're done with it!

```java
mgr.unregisterListener(this);
```

### Using batched sensor data delivery
  
Also new in Android 4.4 is the possibility, where the underlying hardware supports it, to save power by having the sensors collect data while the rest of the system is off, and saving the data in a FIFO buffer.

You can check if the device supports sensors data batching by using the `Sensor#getFifoReservedEventCount` and `Sensor#getFifoMaxEventCount` methods on your sensor instance:

```java
// Determine the reserved FIFO size for the sensor
final int fifoSize = stepCounter.getFifoReservedEventCount();
if (fifoSize > 0) {
    // The device supports batched data delivery
}

// In this case, the device seems not to have an HW-backed
// sensor events buffer.
```

We use a repeating alarm to make sure we don't lose any steps event, as suggested by the Android documentation.

### The strange case of the Step Counter and FIFO size

In the case of a Nexus 5, the Step Counter does support batching _in a way_. Why do we say _in a way_? Well, because both `stepCtr.getFifoReservedEventCount()` and `stepCtr.getFifoMaxEventCount()` return `1`.

This means (we guess) that, while HW batching is indeed supported, the step counter doesn't actually batch up data. By comparison, the step detector on a Nexus 5 has a FIFO size (both max and reserved) of `4900` events.

Thus, in our app, we don't use batched event retrieval for the step counter. We do instead use it for the step detector.

### The step counter zero

The step counter keeps a global steps count, since it was first started by _any_ app after the last boot.

This means that if you're not starting it at boot, it will likely begin at a non-zero count. We take that into account by subtracting the initial count to all subsequent step counter events' values.

## Known issues
* Other installed apps could interfere with the sensor. For example, if you have Moves installed and you suspend tracking, it could prevent FStep from getting the event callbacks. We can't reliably reproduce this to find if Moves is actually the problem or not.
* Sensor event batching will only take place when the device is completely asleep. If the CPU is active for any reason, then your app will get the sensor events right away. This is by Android design.