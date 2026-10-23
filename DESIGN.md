---
name: Game Controller
description: An Android rig controller for Windows games, dressed as a racing-game front end.
colors:
  ground: "#0A0D14"
  panel: "#111622"
  panel-raised: "#171D2B"
  cap: "#1C2333"
  cap-high: "#262F44"
  cap-pressed: "#141A27"
  line: "#2B3448"
  line-strong: "#465170"
  ink: "#F2F4F8"
  ink-muted: "#A0A9BC"
  ink-faint: "#7C869C"
  electric: "#FF2E7E"
  on-electric: "#1F0010"
  signal-amber: "#FFB020"
  signal-yellow: "#FFD43B"
  signal-green: "#34E07F"
  signal-red: "#FF4B3A"
  signal-blue: "#4C9BFF"
  signal-cyan: "#2EE6E6"
typography:
  display:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "36sp"
    fontWeight: 800
    lineHeight: 1.1
    fontStyle: italic
  headline:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: 1.17
    fontStyle: italic
  title:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: 1.2
    fontStyle: italic
  body:
    fontFamily: "Barlow, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.43
  label:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "13sp"
    fontWeight: 600
    letterSpacing: "0.07em"
  cap-label:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    letterSpacing: "0.07em"
  readout:
    fontFamily: "Barlow Condensed, sans-serif"
    fontSize: "34sp"
    fontWeight: 800
    lineHeight: 1
    fontStyle: italic
    fontFeature: "tnum"
rounded:
  cap-corner: "6dp"
  cap-cut: "14dp"
  slant-lean: "0.21"
spacing:
  grid-gap: "8dp"
  panel: "16dp"
  section: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.electric}"
    textColor: "{colors.on-electric}"
    typography: "{typography.label}"
    height: "48dp"
  button-outline:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.electric}"
    typography: "{typography.label}"
    height: "48dp"
  button-quiet:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
  tab-active:
    backgroundColor: "{colors.electric}"
    textColor: "{colors.on-electric}"
    typography: "{typography.label}"
  tab-idle:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
  control-cap:
    backgroundColor: "{colors.cap}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.cap-label}"
    rounded: "{rounded.cap-corner}"
  card-profile:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    padding: "16dp"
  state-tag:
    backgroundColor: "{colors.cap}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
---

# Design System: Game Controller

## Overview

**Creative North Star: "Race Front-End"**

The app reads like the menu and HUD of a racing game: a blue-black pit at night, cool white ink, and a single electric magenta that means *this one is selected, on, or live*. Every surface leans forward. Tabs, title plates, the gear box and buttons are parallelograms; control caps are square-shouldered with the top-right corner cut off like a livery number panel. Names and numbers speak in italic condensed type, the voice of a race-start screen, while explanations stay upright and calm.

It is an operating tool first. The play surface is used at arm's length on a phone or tablet while driving a sim, so everything is large, high contrast and readable in a glance. Decoration exists only where it carries state: the racing stripe on a lit control, the marching slashes on a command still waiting for the game, the half-filled lamp on a link that is connecting.

**Key Characteristics:**
- Dark tonal layers instead of shadows; depth comes from ground → panel → cap steps.
- One house accent (electric magenta); signal colours appear only with meaning.
- Forward-leaning parallelograms for navigation and plates; cut-corner caps for controls.
- Italic condensed numerals with tabular figures for every live number.
- State is never colour alone: lamps fill, stripes slash, text says the word.

## Colors

A night-garage palette: four blue-black steps, three inks, one electric accent, and a strict set of signal colours.

### Primary
- **Electric Magenta** (electric): the selected tab, the primary button, the gear box, the lit wash and stripe on a neutral control, focus outlines. Text on it uses **On-Electric** (on-electric).

### Neutral
- **Pit Black** (ground): the screen behind everything.
- **Garage Panel** (panel) and **Raised Panel** (panel-raised): grouped content, cards, the HUD strip, dialogs.
- **Cap Steel** (cap) to **Cap Highlight** (cap-high): the top-to-bottom gradient on control caps; **Cap Pressed** (cap-pressed) while held.
- **Seam** (line) and **Strong Seam** (line-strong): 1dp outlines and dividers; strong seam for idle outline buttons and focused fields.
- **Track White** (ink), **Muted Ink** (ink-muted), **Faint Ink** (ink-faint): titles and values, secondary labels and idle cap labels, units and hints. Faint ink is the floor for text; nothing dimmer carries words.

### Signals
- **Green** (signal-green) linked or confirmed by the game, **Amber** (signal-amber) waiting or connecting, **Red** (signal-red) critical or failed. **Blue**, **Cyan**, **Yellow** exist only as user-selectable control accents for lights and indicators (a blinker can be amber, a beam can be blue).

### Named Rules
**The One Voice Rule.** Electric magenta is the only accent the app chooses for itself. It marks one selected thing per group: the active tab, the primary action, a lit control. A second electric element in the same row is a bug.

**The Signal Means Something Rule.** Green, amber and red are never decorative. If a surface is green, something is connected or confirmed.

## Typography

**Display Font:** Barlow Condensed (italic and upright, 500 to 800)
**Body Font:** Barlow (400 to 600)

**Character:** Condensed italic for anything you'd see flash up on a race HUD (names, numbers, plates); plain Barlow for sentences, so instructions never shout.

### Hierarchy
- **Display** (800 italic, 36 to 57sp): reserved; screen-level moments only.
- **Headline** (700 to 800 italic, 24 to 32sp): profile card titles, dialog titles.
- **Title** (700 italic, 20sp): the profile name in the play and editor headers, inspector item names.
- **Body** (400, 14 to 16sp, 1.4 to 1.5 line height): descriptions, connection steps, hints.
- **Label** (600, 11 to 15sp, +0.06 to +0.08em, uppercase): buttons, tabs, tags, section headers, cap labels.
- **Readout** (700 to 800 italic, 20 / 34 / 52sp, tabular figures): speed, rpm, gear, slider and knob values. Units sit beneath in 11sp uppercase label at +0.1em.

### Named Rules
**The Italic Is For Names And Numbers Rule.** Italic condensed carries identity and live values. Running text is never italic.

**The Tabular Figures Rule.** Every number that changes while you look at it uses `tnum` so the HUD does not jitter.

## Layout

The play surface is a fixed grid of cells per page (columns × rows from the profile, default 8 × 4) with an 8dp gap, scaled to fill the screen below a single HUD strip. Controls span whole cells. The HUD strip holds back, profile name, scrollable page tabs, game readouts, the two link badges and Edit; when the strip is too narrow the badges drop their names ("BT", "PC") but keep their status words and lamps.

Lists (profiles) use an adaptive grid with a 300dp minimum card width, so a phone shows one column and a tablet three. Forms (connect, inspector, page settings) group into panels with 16dp padding and 24dp between sections. The primary action for a screen sits bottom-end as a slanted button.

Labels on caps hide below 64dp cap height; knob labels show on the play surface whenever there is room and are reserved for 96dp+ in the editor.

## Elevation & Depth

Flat. There are no drop shadows. Depth is tonal: Pit Black ground, Garage Panel for groups, Cap Steel gradient for anything pressable. A pressed cap darkens to Cap Pressed and scales down slightly; a lit cap gets an accent wash and a stripe rather than lifting.

**The Lit Not Lifted Rule.** State changes colour and stripe, never elevation.

## Shapes

Two silhouettes carry the world.

- **The slant** (lean 0.21 × height, about 12°): a parallelogram with both sides leaning forward. Tabs, tags, buttons, the gear box and title plates.
- **The cap** (top-right corner cut 14dp, other corners 6dp): control buttons, switches, sliders' housings. The cut is the hand-made detail that says "this is a physical control".

Outlines are 1dp seams. Disabled or empty cells use a dashed outline rather than lowered opacity alone.

## Components

### Buttons
- **Shape:** the slant.
- **Primary:** Electric Magenta fill, On-Electric label, 48dp tall. One per screen region.
- **Outline:** ground fill with an electric 1.5dp slanted border and electric label; used for Edit in the HUD (min width 104dp).
- **Quiet:** panel fill, muted label; secondary actions like Rescan or Add page.
- **Press:** 70ms press tween, spring release.

### Tabs
- **Style:** row of slanted plates. Active is electric fill with On-Electric label; idle is panel fill with muted label. Page tabs scroll horizontally when they overflow.

### Tags (StateTag)
- **Style:** small slanted plate in label type. Default is cap fill with ink; the *quiet* variant (panel fill, muted ink) is used for passive state such as "Editing" or a control type, so it never competes with the active tab.

### Cards / Containers
- **Profile card:** Garage Panel, 16dp padding, a 120dp layout miniature on top, headline-small italic name, body description, then a meta row of tags (game, BUILT-IN) and stats.
- **Panel:** Garage Panel with a 1dp seam; section headers in label type.

### Inputs / Fields
- **Style:** outlined field on panel, seam border, strong seam or electric on focus. Codes, addresses and keys use Barlow medium with tabular figures.

### Navigation
- **HUD strip:** one row at the top of play and editor screens. Back, italic title, tabs, readouts, link badges, primary action.

### Control Cap (signature)
The pressable unit of the play surface. Cap Steel gradient, cut corner, icon above an uppercase cap label, optional readout. Variants: momentary, latching, selector switch, radio keys, axis slider, rotary knob.
- **Lit:** accent wash plus a **racing stripe** along the bottom: solid with speed slashes when the game confirmed it, marching slashes while the command is still PENDING.
- **Disabled / hidden by condition:** dashed outline, faint ink.

### Pilot Joystick (machines)
A 2×2-minimum plate in the cut-corner cap shape on Garage Panel. It holds a round recessed gate with a 24-step tick ring, faint cross guides, a dashed dead-zone ring at the centre, and a chunky raised knob (cap gradient, rim, accent dimple). Off centre, an accent rod runs from the centre to the knob and the rim turns accent while held. On release the knob springs back (springBack motion). Leaving the dead zone gives one haptic tick.
- **Caution band:** diagonal slashes along the plate's foot in the control's accent. They show at 35% alpha at rest and full accent while the stick is off centre, so they carry state like the racing stripe and never act as pure decoration.

### Hydraulic Lever
A spring-centred bipolar slider draws as a machine valve lever: a 10dp gate, a ball knob instead of the T-handle, and detent notches either side of the rest point. It keeps the slider's tick ladder and the haptic tick when crossing centre.

### Link Badge
Slanted plate with an icon, a name (Bluetooth / PC), a status lamp and a status word or latency ("PAIRED", "9 MS"). The lamp is filled when linked, half-filled when connecting, hollow when off, so state never depends on colour alone.

### Gear Box
The current gear in readout type inside an electric slanted box; the loudest element on the HUD by design.

## Do's and Don'ts

### Do:
- **Do** build every new navigation or action element from the slant (lean 0.21) and every pressable control from the cut-corner cap.
- **Do** keep electric magenta to one selected thing per group.
- **Do** pair every status colour with a word, a lamp fill, or a stripe pattern.
- **Do** set live numbers in italic condensed readout type with tabular figures.
- **Do** step depth through ground → panel → cap tones.

### Don't:
- **Don't** add drop shadows or glows to show elevation or state.
- **Don't** use green, amber or red as decoration.
- **Don't** italicise body copy or set sentences in condensed caps.
- **Don't** introduce a second house accent; extra colours belong only to user-chosen control accents.
- **Don't** use rounded pills or full-radius chips; they break the forward-leaning world.
