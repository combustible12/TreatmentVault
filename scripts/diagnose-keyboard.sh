#!/usr/bin/env bash
set +e
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.menter.treatmentvault.KeyboardScrollDiagnosisTest\#existingNoteFromList --stacktrace
diagnostic_status=$?
mkdir -p keyboard-diagnostics
adb logcat -d -s KeyboardDiag:I '*:S' > keyboard-diagnostics/log.txt
adb pull /sdcard/Android/data/com.menter.treatmentvault/files/diagnosis keyboard-diagnostics/
adb pull /sdcard/Download/keyboard-diagnosis keyboard-diagnostics/
cat keyboard-diagnostics/log.txt
exit "$diagnostic_status"
