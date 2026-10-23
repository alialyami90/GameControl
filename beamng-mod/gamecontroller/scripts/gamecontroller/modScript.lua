-- GameCtl: loads the game engine extension when the mod is active.
-- The extension loads its vehicle part into the player vehicle by itself.
load("gameControllerLink")
setExtensionUnloadMode("gameControllerLink", "manual")
