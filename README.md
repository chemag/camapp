# camapp

camapp is a tool to test cameras in Android.

It provides an easy way to test an android camera encoder by changing parameters like:

* resolution
* framerate
* format
* others


# 1. Prerequisites

For running camapp:
* adb connection to the device being tested


# 2. Operation: Install and Run the App

(a) Build the app
```
$ ./gradlew build
...
BUILD SUCCESSFUL in 1m 15s
57 actionable tasks: 57 executed
```

(b) Install the app
```
$ ./gradlew installDebug
...
Installing APK 'app-debug.apk' on '...' for app:debug
Installed on 1 device.

BUILD SUCCESSFUL in 9s
27 actionable tasks: 1 executed, 26 up-to-date
```

Check the app got installed successfully:

```
$ adb shell pm list packages |grep camapp
package:com.facebook.camapp
```

(c) Run the app (from the CLI).

```
$ adb shell monkey -v -p com.facebook.camapp 1
  bash arg: -v
  bash arg: -p
  bash arg: com.facebook.camapp
  bash arg: 1
args: [-v, -p, com.facebook.camapp, 1]
 arg: "-v"
 arg: "-p"
 arg: "com.facebook.camapp"
 arg: "1"
data="com.facebook.camapp"
:Monkey: seed=... count=1
:AllowPackage: com.facebook.camapp
:IncludeCategory: android.intent.category.LAUNCHER
:IncludeCategory: android.intent.category.MONKEY
// Event percentages:
//   0: 15.0%
//   1: 10.0%
//   2: 2.0%
//   3: 15.0%
//   4: -0.0%
//   5: -0.0%
//   6: 25.0%
//   7: 15.0%
//   8: 2.0%
//   9: 2.0%
//   10: 1.0%
//   11: 13.0%
:Switch: #Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=com.facebook.camapp/.MainActivity;end
    // Allowing start of Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=com.facebook.camapp/.MainActivity } in package com.facebook.camapp
Events injected: 1
:Sending rotation degree=0, persist=false
:Dropped: keys=0 pointers=0 trackballs=0 flips=0 rotations=0
## Network stats: elapsed time=107ms (0ms mobile, 0ms wifi, 107ms not connected)
// Monkey finished
```


# 3. License

camapp is BSD licensed, as found in the [LICENSE](LICENSE) file.
