# zelda3-android
A port of Zelda3 to Android, with a modern touch controller and in-game settings UI. <br>

Original Repository: https://github.com/snesrev/zelda3 <br>
Based on: https://github.com/yeticarus/zelda3-android <br>

Use the instructions on the original repository (or below if you don't have access to a computer) to extract the zelda3_assets.dat file from your rom and put it in Android/data/com.dishii.zelda3/files <br>
Running the app once will create the directory (the app shall crash on first launch, but the directory will be created). <br>
For this version to work, make sure your zelda3.ini file entries match these https://github.com/yeticarus/zelda3-android/blob/main/app/src/main/assets/zelda3.ini <br>

> **Note:** The `zelda3.ini` file has two `Controls =` lines by default. The first one (QWERTY) must be commented out with a `#` for the touch controller to work correctly. Only the Android line should be active:
> ```
> #Controls = Up, Down, Left, Right, Right Shift, Return, x, z, s, a, c, v
> Controls = y, h, g, j, v, n, x, z, s, a, c, v
> ```

---

## What's new in this fork

The following changes were made by an AI assistant (Claude) and not by the original author:

### Modern Touch Controller
- The old XML-based button layout (`layout.xml`) and all drawable assets (`button_a.png`, `dpad.png`, etc.) have been removed and replaced entirely with a programmatic controller built in `MainActivity.java`
- All controller buttons are drawn in code — no external resources required
- The controller fades in on first touch and is invisible until then

### Floating D-Pad
- The D-Pad no longer sits in a fixed position on screen
- It appears exactly where the player touches the left half of the screen, centred on the finger
- It disappears with a fade animation when the finger is lifted
- Direction is calculated using a full 360° angle (not a fixed grid), so sliding between directions works seamlessly without lifting the finger

### Left-Side Drawer Menu
- A draggable `☰` tab sits on the left edge of the screen
- Dragging it up and down repositions it to wherever is most comfortable
- Tapping it opens a slide-in drawer panel with three tabs:

#### 💾 Saves Tab
- 10 save slots, each showing a screenshot thumbnail taken at save time
- Layout per slot: **[Save]** on the left — **Slot number + timestamp** centred — **[Load]** on the right
- Screenshots are captured via `PixelCopy` targeting the SDL surface directly (requires Android 8+), with the controller overlay hidden during capture so the screenshot shows only the game
- Thumbnails persist between sessions and are stored in `saves/thumbs/`
- The Load button is greyed out until a save exists for that slot

#### ⚙ Settings Tab
- All `zelda3.ini` settings are editable directly from within the game — no file manager required
- Changes are written to `zelda3.ini` immediately
- A **"Restart game to apply changes"** banner appears after any change, since most settings take effect on next launch
- Settings are grouped into four categories:
  - **General** — Autosave, Aspect Ratio, Disable frame delay, Skip intro
  - **Graphics** — New renderer, Enhanced Mode 7, Sprite limits, Linear filtering, Dim flashes, Ignore aspect ratio
  - **Sound** — Enable audio, Frequency, Channels (Mono/Stereo), Buffer size
  - **Features** — All 13 feature toggles including ItemSwitchLR, bug fixes, rupee cap, and more

#### 🎮 Controller Tab
- **Opacity slider** — adjusts the transparency of all controller buttons in real time
- **Turbo toggle** — turns turbo mode on/off; stays active until toggled off (green = on, grey = off)

---

## Requirements

- Android 8.0 (API 26) or higher — required for `PixelCopy` screenshot capture
- The `zelda3_assets.dat` file extracted from a legal copy of the ROM

---

## How to Change Settings

Settings can now be changed directly in the in-game drawer (⚙ Settings tab) without editing any files manually. <br>
If you prefer to edit manually: `Android/data/com.dishii.zelda3/files/zelda3.ini`

---

## Instructions for creating zelda3_assets.dat on Android

1. Download PyDroid: https://play.google.com/store/apps/details?id=ru.iiec.pydroid3&hl=en_US. Choose to skip any options that ask for money, you can do all of the following steps without paying.<br>
2. Open the hamburger menu at the top left of the app and select Pip.<br>
3. Type in "Pillow" without the quotes and it will have you install the repository app from the app store.<br>
4. Once the repository app is installed, you can install "Pillow" and "pyyaml".<br>
5. Download the source code zip file for zelda3 at https://github.com/snesrev/zelda3/releases/tag/v0.3 <br>
6. Extract the zip file.<br>
7. Place your rom file in the main zelda3 directory that you extracted, the same one as extract_assets.bat, and rename it to zelda3.sfc<br>
8. Open PyDroid again, open the hamburger menu, and select Terminal.<br>
9. Navigate to where you placed the rom file. (If you are unfamiliar with terminal commands, "ls" lists the folders and files and "cd Foldername" changes the directory. An example using the 0.3 release of zelda3 above would be "cd Download" "cd zelda3-0.3" "cd zelda3-0.3" or simply "cd Download/zelda3-0.3/zelda3-0.3")<br>
10. Paste in this command `python3 assets/restool.py --extract-from-rom`<br>
11. It should pause for a while and when it finishes you should be able to see zelda3_assets.dat in the same folder as your rom. You can go ahead and copy that to the Android/data/com.dishii.zelda3/files location.<br>
12. Copy https://github.com/yeticarus/zelda3-android/blob/main/app/src/main/assets/zelda3.ini to Android/data/com.dishii.zelda3/files. Make sure to comment out the first Controls line as described at the top of this README.
