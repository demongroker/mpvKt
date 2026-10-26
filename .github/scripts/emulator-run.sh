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

adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

adb install -r -g "${APK}" 2>&1 | tee -a report/install.txt
INSTALL_RC="${PIPESTATUS[0]}"
echo "install_rc=${INSTALL_RC}" | tee -a report/install.txt
if [ "${INSTALL_RC}" -ne 0 ]; then
  adb logcat -d > report/logcat-install-failure.txt 2>/dev/null || true
  exit "${INSTALL_RC}"
fi

adb shell am start -n app.vela.player/live.mehiz.mpvkt.MainActivity 2>&1 | tee -a report/launch.txt
sleep 12
adb exec-out screencap -p > report/home.png || true
adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -5 >> report/launch.txt || true
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
