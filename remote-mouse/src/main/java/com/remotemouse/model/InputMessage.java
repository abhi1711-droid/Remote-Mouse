package com.remotemouse.model;

import java.util.List;

/**
 * Unified input message received over the WebSocket connection.
 * <p>
 * Only the {@code type} field is always present. Other fields are populated
 * depending on the message type (e.g. a "move" message uses {@code dx}/{@code dy},
 * a "click" message uses {@code button}, etc.). Unused fields will be {@code null}.
 *
 * @param type      message type: move, click, doubleclick, scroll, drag, type, key, media, sensitivity
 * @param button    mouse button for click events (LEFT, RIGHT, MIDDLE)
 * @param dx        horizontal delta for move/drag events
 * @param dy        vertical delta for move/drag events
 * @param delta     scroll wheel delta
 * @param text      text to type for "type" events
 * @param key       key name for "key" events
 * @param modifiers modifier keys held during "key" events (CTRL, ALT, SHIFT, META)
 * @param action    sub-action: drag phase (start/move/end), or media action (PLAY_PAUSE, VOLUME_UP, etc.)
 * @param value     numeric value for sensitivity adjustment
 */
public record InputMessage(
        String type,
        String button,
        Integer dx,
        Integer dy,
        Integer delta,
        String text,
        String key,
        List<String> modifiers,
        String action,
        Double value
) {}
