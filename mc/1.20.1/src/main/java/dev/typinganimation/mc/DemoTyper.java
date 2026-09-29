package dev.typinganimation.mc;

import net.minecraft.client.gui.components.EditBox;

import java.util.Random;

/**
 * The config screen's "Demo": types a phrase into the preview with human-like timing (uneven key intervals, pauses
 * after words and punctuation, the odd typo that gets corrected), erases part of it, puts it back (alternately by
 * retyping and as one paste), clears the field and starts over, until stopped. Driven by frame time.
 */
final class DemoTyper {
    private enum Phase { TYPE, TYPO_PAUSE, TYPO_FIX, HOLD, ERASE, HOLD_ERASED, REFILL, HOLD_FULL, CLEAR_PAUSE }

    /** Letters a typo is picked from, by the script of the intended letter (the phrase is localized). */
    private static final String LATIN = "qwertyuiopasdfghjklzxcvbnm";
    private static final String CYRILLIC = "йцукенгшщзхфывапролджэячсмитьбю";

    private final Random random = new Random(0x7A11_5EEDL);
    private boolean running;
    private Phase phase = Phase.TYPE;
    private long nextAt;
    private String phrase = "";
    /** Next index of {@link #phrase} to type. */
    private int pos;
    private int eraseTo;
    private boolean pasteRefill;
    private int loops;

    boolean running() {
        return running;
    }

    void start(EditBox box, String phrase, long now) {
        this.phrase = phrase == null || phrase.isEmpty() ? "Typing Animation" : phrase;
        running = true;
        loops = 0;
        box.setValue("");
        toEnd(box);
        pos = 0;
        phase = Phase.TYPE;
        nextAt = now + 250;
    }

    void stop() {
        running = false;
    }

    void update(EditBox box, long now) {
        if (!running || box == null) {
            return;
        }
        if (now - nextAt > 400) {
            nextAt = now; // the game stalled (or the screen was hidden): do not replay a burst of keystrokes
        }
        for (int guard = 0; running && now >= nextAt && guard < 4; guard++) {
            step(box);
        }
    }

    private void step(EditBox box) {
        switch (phase) {
            case TYPE -> {
                if (pos >= phrase.length()) {
                    phase = Phase.HOLD;
                    after(1300, 300);
                    return;
                }
                int cp = phrase.codePointAt(pos);
                String typo = pos > 2 && random.nextInt(30) == 0 ? typoFor(cp) : null;
                if (typo != null) {
                    // a typo: a wrong letter, a short "oops" pause, then backspace
                    type(box, typo);
                    phase = Phase.TYPO_PAUSE;
                    after(260, 160);
                    return;
                }
                String s = new String(Character.toChars(cp));
                type(box, s);
                pos += s.length();
                after(keyDelay(cp), 0);
            }
            case TYPO_PAUSE -> {
                backspace(box);
                phase = Phase.TYPO_FIX;
                after(140, 80);
            }
            case TYPO_FIX -> {
                phase = Phase.TYPE;
                after(40, 40);
            }
            case HOLD -> {
                eraseTo = wordBoundaryNear(phrase, phrase.length() * (35 + random.nextInt(30)) / 100);
                phase = Phase.ERASE;
                after(0, 0);
            }
            case ERASE -> {
                if (box.getValue().length() > eraseTo) {
                    backspace(box);
                    after(38, 30); // holding backspace is faster than typing
                } else {
                    phase = Phase.HOLD_ERASED;
                    after(550, 200);
                }
            }
            case HOLD_ERASED -> {
                pasteRefill = !pasteRefill;
                if (pasteRefill) {
                    type(box, phrase.substring(Math.min(eraseTo, phrase.length())));
                    pos = phrase.length();
                    phase = Phase.HOLD_FULL;
                    after(1500, 300);
                } else {
                    pos = Math.min(eraseTo, phrase.length());
                    phase = Phase.REFILL;
                    after(0, 0);
                }
            }
            case REFILL -> {
                if (pos >= phrase.length()) {
                    phase = Phase.HOLD_FULL;
                    after(1300, 300);
                } else {
                    int cp = phrase.codePointAt(pos);
                    String s = new String(Character.toChars(cp));
                    type(box, s);
                    pos += s.length();
                    after(keyDelay(cp), 0);
                }
            }
            case HOLD_FULL -> {
                box.setValue(""); // everything leaves at once
                loops++;
                phase = Phase.CLEAR_PAUSE;
                after(800, 200);
            }
            case CLEAR_PAUSE -> {
                pos = 0;
                phase = Phase.TYPE;
                after(0, 0);
            }
        }
    }

    private void type(EditBox box, String s) {
        toEnd(box);
        box.insertText(s);
    }

    private void backspace(EditBox box) {
        toEnd(box);
        box.deleteChars(-1);
    }

    /**
     * Caret (and selection anchor) to the end. Not {@code moveCursorToEnd()}: in 1.20.x it keeps the selection anchor
     * while the box's {@code shiftPressed} flag is set, which stays set after a lone Shift press (EditBox has no
     * keyReleased), so the next insert would replace a selection.
     */
    private static void toEnd(EditBox box) {
        int end = box.getValue().length();
        box.setCursorPosition(end);
        box.setHighlightPos(end);
    }

    /** A wrong letter of the same script and case as {@code cp}, or null when {@code cp} gets no typo. */
    private String typoFor(int cp) {
        if (!Character.isLetter(cp)) {
            return null;
        }
        final String set;
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.LATIN) {
            set = LATIN;
        } else if (script == Character.UnicodeScript.CYRILLIC) {
            set = CYRILLIC;
        } else {
            return null;
        }
        int lower = Character.toLowerCase(cp);
        int wrong = set.charAt(random.nextInt(set.length()));
        if (wrong == lower) {
            wrong = set.charAt((set.indexOf(wrong) + 1) % set.length());
        }
        if (Character.isUpperCase(cp)) {
            wrong = Character.toUpperCase(wrong);
        }
        return new String(Character.toChars(wrong));
    }

    private long keyDelay(int cp) {
        long d = 55 + random.nextInt(75);
        if (cp == ' ') {
            d += 40 + random.nextInt(90);
        } else if (cp == ',' || cp == '.' || cp == '!' || cp == '?') {
            d += 150 + random.nextInt(150);
        }
        if (random.nextInt(12) == 0) {
            d += 120 + random.nextInt(160); // a moment of hesitation
        }
        return d;
    }

    private void after(long base, int jitter) {
        nextAt += base + (jitter > 0 ? random.nextInt(jitter) : 0);
    }

    private static int wordBoundaryNear(String s, int target) {
        target = Math.max(1, Math.min(s.length() - 1, target));
        int left = s.lastIndexOf(' ', target);
        return left > 0 ? left + 1 : target;
    }

    int loops() {
        return loops;
    }
}
