package com.remotemouse.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates remote input commands into host-side mouse/keyboard actions
 * via {@link java.awt.Robot}.
 * <p>
 * All public methods are synchronized on the {@code robot} instance to
 * guarantee thread safety when called concurrently from WebSocket handlers.
 */
@Service
public class RobotService {

    private static final Logger log = LoggerFactory.getLogger(RobotService.class);

    /** Comprehensive name → VK_ constant mapping, populated once in a static block. */
    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        // Letters A-Z
        for (char c = 'A'; c <= 'Z'; c++) {
            KEY_MAP.put(String.valueOf(c), KeyEvent.VK_A + (c - 'A'));
        }
        // Digits 0-9
        for (int i = 0; i <= 9; i++) {
            KEY_MAP.put(String.valueOf(i), KeyEvent.VK_0 + i);
        }
        // Function keys F1-F12
        for (int i = 1; i <= 12; i++) {
            KEY_MAP.put("F" + i, KeyEvent.VK_F1 + (i - 1));
        }
        // Arrow keys
        KEY_MAP.put("UP", KeyEvent.VK_UP);
        KEY_MAP.put("DOWN", KeyEvent.VK_DOWN);
        KEY_MAP.put("LEFT", KeyEvent.VK_LEFT);
        KEY_MAP.put("RIGHT", KeyEvent.VK_RIGHT);
        // Special keys
        KEY_MAP.put("ENTER", KeyEvent.VK_ENTER);
        KEY_MAP.put("RETURN", KeyEvent.VK_ENTER);
        KEY_MAP.put("ESCAPE", KeyEvent.VK_ESCAPE);
        KEY_MAP.put("ESC", KeyEvent.VK_ESCAPE);
        KEY_MAP.put("TAB", KeyEvent.VK_TAB);
        KEY_MAP.put("BACKSPACE", KeyEvent.VK_BACK_SPACE);
        KEY_MAP.put("DELETE", KeyEvent.VK_DELETE);
        KEY_MAP.put("SPACE", KeyEvent.VK_SPACE);
        KEY_MAP.put("HOME", KeyEvent.VK_HOME);
        KEY_MAP.put("END", KeyEvent.VK_END);
        KEY_MAP.put("PAGE_UP", KeyEvent.VK_PAGE_UP);
        KEY_MAP.put("PAGEUP", KeyEvent.VK_PAGE_UP);
        KEY_MAP.put("PAGE_DOWN", KeyEvent.VK_PAGE_DOWN);
        KEY_MAP.put("PAGEDOWN", KeyEvent.VK_PAGE_DOWN);
        KEY_MAP.put("INSERT", KeyEvent.VK_INSERT);
        KEY_MAP.put("CAPS_LOCK", KeyEvent.VK_CAPS_LOCK);
        KEY_MAP.put("CAPSLOCK", KeyEvent.VK_CAPS_LOCK);
        KEY_MAP.put("NUM_LOCK", KeyEvent.VK_NUM_LOCK);
        KEY_MAP.put("NUMLOCK", KeyEvent.VK_NUM_LOCK);
        KEY_MAP.put("SCROLL_LOCK", KeyEvent.VK_SCROLL_LOCK);
        KEY_MAP.put("SCROLLLOCK", KeyEvent.VK_SCROLL_LOCK);
        KEY_MAP.put("PRINT_SCREEN", KeyEvent.VK_PRINTSCREEN);
        KEY_MAP.put("PRINTSCREEN", KeyEvent.VK_PRINTSCREEN);
        // Modifiers (also usable as standalone keys)
        KEY_MAP.put("CTRL", KeyEvent.VK_CONTROL);
        KEY_MAP.put("CONTROL", KeyEvent.VK_CONTROL);
        KEY_MAP.put("ALT", KeyEvent.VK_ALT);
        KEY_MAP.put("SHIFT", KeyEvent.VK_SHIFT);
        KEY_MAP.put("META", KeyEvent.VK_META);
        KEY_MAP.put("CMD", KeyEvent.VK_META);
        KEY_MAP.put("COMMAND", KeyEvent.VK_META);
        // Symbols
        KEY_MAP.put("COMMA", KeyEvent.VK_COMMA);
        KEY_MAP.put("PERIOD", KeyEvent.VK_PERIOD);
        KEY_MAP.put("SLASH", KeyEvent.VK_SLASH);
        KEY_MAP.put("BACKSLASH", KeyEvent.VK_BACK_SLASH);
        KEY_MAP.put("SEMICOLON", KeyEvent.VK_SEMICOLON);
        KEY_MAP.put("QUOTE", KeyEvent.VK_QUOTE);
        KEY_MAP.put("OPEN_BRACKET", KeyEvent.VK_OPEN_BRACKET);
        KEY_MAP.put("CLOSE_BRACKET", KeyEvent.VK_CLOSE_BRACKET);
        KEY_MAP.put("MINUS", KeyEvent.VK_MINUS);
        KEY_MAP.put("EQUALS", KeyEvent.VK_EQUALS);
        KEY_MAP.put("BACK_QUOTE", KeyEvent.VK_BACK_QUOTE);
        KEY_MAP.put("BACKQUOTE", KeyEvent.VK_BACK_QUOTE);
    }

    private final Robot robot;
    private double sensitivity = 1.5;
    private boolean isDragging = false;
    private Dimension screenSize;

    /**
     * Creates the AWT Robot instance. Fails fast if the platform does not
     * support robot-driven input (e.g. headless server).
     */
    public RobotService() throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(0); // no artificial delay between events
        log.info("java.awt.Robot initialized successfully");
    }

    /**
     * Validates that the Robot can query the display and logs the screen size.
     */
    @PostConstruct
    public void init() {
        this.screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        log.info("Screen size detected: {}x{}", screenSize.width, screenSize.height);
    }

    // ────────────────────────── Mouse ──────────────────────────

    /**
     * Moves the mouse pointer by the given deltas, scaled by the current
     * sensitivity factor and clamped to screen bounds.
     *
     * @param dx horizontal pixel delta (before sensitivity)
     * @param dy vertical pixel delta (before sensitivity)
     */
    public void moveMouse(int dx, int dy) {
        synchronized (robot) {
            Point current = MouseInfo.getPointerInfo().getLocation();
            int newX = (int) Math.round(current.x + dx * sensitivity);
            int newY = (int) Math.round(current.y + dy * sensitivity);
            // Clamp to screen bounds
            newX = Math.max(0, Math.min(newX, screenSize.width - 1));
            newY = Math.max(0, Math.min(newY, screenSize.height - 1));
            robot.mouseMove(newX, newY);
        }
    }

    /**
     * Performs a single mouse click (press + release) for the named button.
     *
     * @param button one of LEFT, RIGHT, MIDDLE (case-insensitive)
     */
    public void click(String button) {
        int mask = mapButton(button);
        synchronized (robot) {
            robot.mousePress(mask);
            robot.mouseRelease(mask);
        }
    }

    /**
     * Performs a double left-click with a 50 ms pause between clicks.
     */
    public void doubleClick() {
        synchronized (robot) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    /**
     * Scrolls the mouse wheel by the given delta.
     *
     * @param delta positive = scroll down, negative = scroll up
     */
    public void scroll(int delta) {
        synchronized (robot) {
            robot.mouseWheel(delta);
        }
    }

    // ────────────────────────── Drag ──────────────────────────

    /**
     * Begins a drag operation by pressing the left mouse button.
     */
    public void dragStart() {
        synchronized (robot) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            isDragging = true;
            log.debug("Drag started");
        }
    }

    /**
     * Continues a drag by moving the pointer (only if a drag is active).
     *
     * @param dx horizontal delta
     * @param dy vertical delta
     */
    public void dragMove(int dx, int dy) {
        synchronized (robot) {
            if (isDragging) {
                moveMouse(dx, dy);
            }
        }
    }

    /**
     * Ends a drag operation by releasing the left mouse button.
     */
    public void dragEnd() {
        synchronized (robot) {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            isDragging = false;
            log.debug("Drag ended");
        }
    }

    // ────────────────────────── Keyboard ──────────────────────────

    /**
     * Types a string one character at a time. Uppercase characters are
     * handled by pressing Shift automatically.
     *
     * @param text the text to type
     */
    public void typeText(String text) {
        synchronized (robot) {
            for (char c : text.toCharArray()) {
                typeCharacter(c);
            }
        }
    }

    /**
     * Presses a named key with optional modifier keys.
     * <p>
     * Modifiers are pressed first (in order), then the primary key is
     * pressed and released, and finally modifiers are released in reverse.
     *
     * @param key       key name (e.g. "A", "ENTER", "F5")
     * @param modifiers modifier names (e.g. ["CTRL", "SHIFT"])
     */
    public void pressKey(String key, List<String> modifiers) {
        Integer keyCode = KEY_MAP.get(key.toUpperCase());
        if (keyCode == null) {
            log.warn("Unknown key name: '{}' – ignoring", key);
            return;
        }

        synchronized (robot) {
            // Press modifiers
            int[] modCodes = resolveModifiers(modifiers);
            for (int mod : modCodes) {
                robot.keyPress(mod);
            }
            // Press + release primary key
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            // Release modifiers in reverse
            for (int i = modCodes.length - 1; i >= 0; i--) {
                robot.keyRelease(modCodes[i]);
            }
        }
    }

    // ────────────────────────── Media ──────────────────────────

    /**
     * Simulates a media key action. On macOS many media keys lack direct
     * VK_ mappings; this MVP uses common fallback keycodes.
     *
     * @param action one of PLAY_PAUSE, VOLUME_UP, VOLUME_DOWN, MUTE, NEXT, PREVIOUS
     */
    public void mediaKey(String action) {
        if (action == null) {
            log.warn("mediaKey called with null action");
            return;
        }
        synchronized (robot) {
            switch (action.toUpperCase()) {
                case "PLAY_PAUSE" -> pressAndRelease(KeyEvent.VK_SPACE); // common fallback
                case "VOLUME_UP" -> pressAndRelease(KeyEvent.VK_F12, KeyEvent.VK_SHIFT);
                case "VOLUME_DOWN" -> pressAndRelease(KeyEvent.VK_F11, KeyEvent.VK_SHIFT);
                case "MUTE" -> pressAndRelease(KeyEvent.VK_F10, KeyEvent.VK_SHIFT);
                case "NEXT" -> pressAndRelease(KeyEvent.VK_RIGHT, KeyEvent.VK_META);
                case "PREVIOUS" -> pressAndRelease(KeyEvent.VK_LEFT, KeyEvent.VK_META);
                default -> log.warn("Unknown media action: '{}'", action);
            }
        }
    }

    // ────────────────────────── Settings ──────────────────────────

    /**
     * Adjusts the mouse sensitivity multiplier, clamped to [0.5, 5.0].
     *
     * @param value desired sensitivity
     */
    public void setSensitivity(double value) {
        this.sensitivity = Math.max(0.5, Math.min(5.0, value));
        log.info("Sensitivity set to {}", this.sensitivity);
    }

    /**
     * Returns the current screen dimensions.
     *
     * @return screen size as {@link Dimension}
     */
    public Dimension getScreenSize() {
        return screenSize;
    }

    // ────────────────────────── Helpers ──────────────────────────

    private int mapButton(String button) {
        if (button == null) {
            return InputEvent.BUTTON1_DOWN_MASK;
        }
        return switch (button.toUpperCase()) {
            case "RIGHT" -> InputEvent.BUTTON3_DOWN_MASK;
            case "MIDDLE" -> InputEvent.BUTTON2_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }

    private int[] resolveModifiers(List<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return new int[0];
        }
        return modifiers.stream()
                .map(String::toUpperCase)
                .map(KEY_MAP::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private void typeCharacter(char c) {
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            log.debug("No key code for character '{}' (U+{}) – skipping", c,
                    String.format("%04X", (int) c));
            return;
        }
        boolean isUpper = Character.isUpperCase(c);
        if (isUpper) {
            robot.keyPress(KeyEvent.VK_SHIFT);
        }
        try {
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
        } catch (IllegalArgumentException e) {
            log.debug("Could not type character '{}': {}", c, e.getMessage());
        }
        if (isUpper) {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    /**
     * Press and immediately release a single key (no modifiers).
     */
    private void pressAndRelease(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    /**
     * Press modifier, press key, release key, release modifier.
     */
    private void pressAndRelease(int keyCode, int modifierCode) {
        robot.keyPress(modifierCode);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.keyRelease(modifierCode);
    }
}
