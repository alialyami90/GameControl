# Lead developer: round 6 replies

- R6-1 Fixed. Crane "Hook view" is button 10; Rear view stays 12.
- R6-2 Fixed. Kickdown 18, Travel speed 24, Engine speed 25/26: every machine button is 32 or below. Test `machineButtonsFitIn32AndAreUniquePerPage` checks range and per-page uniqueness.
- Schema: `ProfileJson.SCHEMA = 2`; `export` writes 2 only when the profile has a joystick, else 1, so joystick-free profiles still open in schema-1 apps. Test `joystickProfilesExportAsSchema2AndKeepActionY` covers the actionY round trip.
- Round 5 INSTALL note (several phones pair Bluetooth and Wi-Fi by order) added under Bluetooth step 3.
- Bluetooth scan off the tick thread: Windows thread.
