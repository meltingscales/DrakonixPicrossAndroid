apk := "app/build/outputs/apk/debug/app-debug.apk"

default:
    @just --list

# Build the debug APK
build:
    ./gradlew assembleDebug

# Build a release bundle (Play Store upload format)
bundle:
    ./gradlew bundleRelease

# Install the debug APK on the connected device
install: build
    adb install -r {{apk}}

# Launch the app on the connected device
launch:
    adb shell am start -n com.drakonix.picross/.MainActivity

# Build, install, and launch in one go
run: install launch

# Uninstall from the connected device (wipes solved progress)
uninstall:
    adb uninstall com.drakonix.picross

# Remove build outputs
clean:
    ./gradlew clean

# Pull a screenshot from the device into ./screenshots/
# (exec-out screencap returns black frames on some devices, so go via a file)
screenshot:
    mkdir -p screenshots
    adb shell screencap -p /sdcard/Download/picross-shot.png
    adb pull /sdcard/Download/picross-shot.png screenshots/$(date +%Y%m%d-%H%M%S).png
    adb shell rm /sdcard/Download/picross-shot.png
