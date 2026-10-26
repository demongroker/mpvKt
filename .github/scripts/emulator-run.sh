#!/usr/bin/env bash
# Runs ON the emulator host, inside reactivecircus/android-emulator-runner.
# The action executes each line of its `script` in a separate shell, so everything
# that needs shared state lives in this file and is invoked as one command.
set -uo pipefail

mkdir -p report
APK="$(tail -1 report/apk_path.txt 2>/dev/null || true)"
echo "apk_path=${APK}"
if [ ! -f "${APK}" ]; then
  echo "APK not found at '${APK}'" | tee -a report/install.txt
  exit 1
fi

adb wait-for-device
echo "boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')" | tee -a report/boot.txt

# KVM state as the emulator user sees it (the action has enabled group perms by now).
{
  echo "identity: $(id)"
  echo "kvm_writable_now: $([ -w /dev/kvm ] && echo yes || echo no)"
  echo "kvm_device_now: $(ls -l /dev/kvm 2>&1)"
} | tee -a report/kvm.txt

for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "${setting}" 0 >/dev/null 2>&1 || true
done

adb install -r -g "${APK}" 2>&1 | tee -a report/install.txt
INSTALL_RC="${PIPESTATUS[0]}"
echo "install_rc=${INSTALL_RC}" | tee -a report/install.txt
if [ "${INSTALL_RC}" -ne 0 ]; then
  adb logcat -d > report/logcat-install-failure.txt 2>/dev/null || true
  exit "${INSTALL_RC}"
fi

# Unaccelerated emulators throw "System UI isn't responding" over the app. Wait it
# out, dismiss it, and only then take the picture.
dismiss_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  for _ in 1 2 3; do
    if adb shell dumpsys window 2>/dev/null | grep -q 'Application Not Responding'; then
      adb shell input keyevent 4 >/dev/null 2>&1 || true
      sleep 2
    else
      return 0
    fi
  done
}

sleep 20
dismiss_system_dialogs
adb shell am force-stop app.vela.player >/dev/null 2>&1 || true
adb shell am start -n app.vela.player/live.mehiz.mpvkt.MainActivity 2>&1 | tee -a report/launch.txt
sleep 25
dismiss_system_dialogs
sleep 3
adb exec-out screencap -p > report/home.png || true

adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -6 | tee report/focus.txt
if grep -q 'Application Not Responding' report/focus.txt; then
  echo "note: a system dialog was still in front; home.png may show it" | tee -a report/focus.txt
fi
adb shell pm list packages 2>/dev/null | grep -i vela >> report/launch.txt || true
adb logcat -d -t 500 > report/logcat.txt 2>/dev/null || true

TEST_COMMAND="$(cat report/test_command.txt 2>/dev/null || true)"
if [ -n "${TEST_COMMAND}" ]; then
  echo "running: ${TEST_COMMAND}" | tee -a report/test.txt
  bash -lc "${TEST_COMMAND}" >> report/test.txt 2>&1
  TEST_RC=$?
  echo "test_exit=${TEST_RC}" | tee -a report/test.txt
else
  echo "no test_command given — install + screenshot only" | tee -a report/test.txt
  TEST_RC=0
fi

mkdir -p report/androidtest
cp -r app/build/reports/androidTests report/androidtest/ 2>/dev/null || true
cp -r app/build/outputs/androidTest-results report/androidtest/ 2>/dev/null || true
adb logcat -d > report/logcat-full.txt 2>/dev/null || true
ls -la report

exit "${TEST_RC}"
