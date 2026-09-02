package com.anland.consumer.hud;

/**
 * Common interface for tracking and injecting hardware modifier keys (Ctrl, Alt, Shift, Super)
 * shared by both the legacy ExtraKeysBar and the new HUD overlay dock strip.
 * This allows Gboard/SystemIME to inject modifier combos uniformly regardless of the active UI mode.
 */
public interface IModifierProvider {

    /**
     * @return True if any latched or locked modifier key is currently active.
     */
    boolean hasActiveModifier();

    /**
     * Emits the modifier(s) down, the target character down/up, and the modifier(s) up,
     * then resets unlocked modifiers to inactive state.
     *
     * @param evdevScancode The target character scan code to combo with the active modifiers.
     */
    void sendKeyComboFromExternal(int evdevScancode);

    /**
     * Clears all modifier states.
     */
    void reset();
}
