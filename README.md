# Overview

DroidGlove is an Android app for controlling the motion of hand
in virtual reality world by the software such as Unity.
That's like the legendary NES controller.

The latest version of javaosc is available at
<https://github.com/cubic9com/DroidGlove/> .


# Folder structure

* `src/`                     DroidGlove sources
* `res/`                     DroidGlove resources


# How to use sources

DroidGlove requires JavaOSC, a library for Open Sound Control,
which was developed by C. Ramakrishnan, Illposed Software.

1. Get JavaOSC at following URL.
<https://github.com/hoijui/JavaOSC/tree/e2a1667cb198675958012d91a374f9e9705195be>
2. Copy `modules/core/src/main/java/com` in JavaOSC to `src/` in DroidGlove.
3. Import that to a development environment such as the Eclipse IDE with built-in ADT.
4. Export app (.apk) .


# How to use app

1. check the IP address of your PC by ipconfig etc.

2. Install DroidGlove.

3. Input the IP address of your PC to the app.

4. Launch the PC software which support DroidGlove such as Mikujalus.

5. Calibrate direction. Aim the top of your phone at the screen of your PC, and push reset button.

6. Then, turn your phone. If you want to grip, swipe down on DroidGlove.


# Thanks

Thanks to C. Ramakrishnan, Illposed Software, who developed JavaOSC.
