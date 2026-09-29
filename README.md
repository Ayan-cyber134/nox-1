# RONiN BLE Test

Standalone Android BLE diagnostic app for the JieLi-style GATT path found in the RONiN STUDIO APK.

## UUIDs
- Service: `0000AE00-0000-1000-8000-00805F9B34FB`
- Write: `0000AE01-0000-1000-8000-00805F9B34FB`
- Notify: `0000AE02-0000-1000-8000-00805F9B34FB`
- CCCD: `00002902-0000-1000-8000-00805F9B34FB`

## Test flow
1. Build/install.
2. Grant Bluetooth permissions.
3. Tap Scan and select the device.
4. The app requests MTU 247, discovers AE00, subscribes to AE02 by writing `01 00` to its CCCD, and logs all notifications.
5. Use **Send Set Volume** to send the extracted RCSP volume frame.
6. Use **Send HEX** for arbitrary packets.

The app deliberately does not assume that FF16/FF17/FF18 are equivalent to the AE00/AE01/AE02 transport. It prints the discovered services when AE00 is absent.

## RCSP volume frame used by the test button
`FE DC BA C0 08 00 04 SN 0F 02 01 VV EF`

`SN` increments from 00; `VV` is the entered 0-255 volume byte.

## Build without Android Studio (recommended for Sharp AQUOS R2 706SH / Android 10)

You can build the APK in GitHub Actions from a browser; Android Studio is not required on your phone or PC.

1. Create a GitHub account at https://github.com/ if you do not already have one.
2. Create a new empty repository, e.g. `RoninBleTest`.
3. Upload the contents of this folder to the repository (including `.github/workflows/build-apk.yml`).
4. Open the repository's **Actions** tab and run **Build APK** (or push to `main`).
5. When the workflow finishes, open the run and download the artifact named **RoninBleTest-debug**.
6. Extract it and install `app-debug.apk` on the Sharp AQUOS R2 706SH.

The project uses Java 17 + Android Gradle Plugin 8.5.2 and targets modern Android while retaining Android 10 support. The 706SH is an ARM64 Android 10 device, and the APK is built as a normal universal debug APK.

If Android blocks installation, enable **Install unknown apps** for the browser/file manager you used to open the APK.
