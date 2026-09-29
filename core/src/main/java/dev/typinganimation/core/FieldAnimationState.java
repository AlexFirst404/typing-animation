package dev.typinganimation.core;

import java.util.Arrays;

/**
 * Animation state of one text widget instance. Per frame the renderer calls, in this order:
 * <ol>
 *   <li>{@link #beginFrame}, {@link #sync};</li>
 *   <li>{@link #caretX} with this frame's caret target, as soon as that target is known — preferably here,
 *       before {@link #isIdle}, so that {@code isIdle} also sees a caret move that starts in this frame;</li>
 *   <li>optionally {@link #isIdle};</li>
 *   <li>{@link #layoutChar} for every visible character with {@link #appearTransform}, the ghosts
 *       ({@link #ghostCount}/{@link #ghost}/{@link #ghostTransform});</li>
 *   <li>{@link #caretX}, if it was not called before {@code isIdle};</li>
 *   <li>{@link #endFrame}.</li>
 * </ol>
 * The idle fast path ({@code isIdle} true: the text may be drawn the vanilla way) covers the <em>text only</em>.
 * The caret must go through {@link #caretX} on every frame in which the field shows a caret, idle or not.
 * {@code isIdle} judges the caret by the most recent {@code caretX} call. When {@code caretX} comes after
 * {@code isIdle}, a caret-only move (arrow keys, Home/End, click) that starts in this frame is visible to
 * {@code isIdle} only from the next frame on. That is harmless because the settled text is pixel-identical on
 * both paths, but the caret itself must still be drawn at {@code caretX}'s result.
 *
 * <p>Per-character data is kept in parallel arrays indexed by UTF-16 index of the current value; an edit is
 * detected as common prefix + common suffix (never splitting a surrogate pair) and the arrays are shifted so
 * every character keeps its identity (birth time, smoothed x, last layout). Arrays grow geometrically and are
 * reused: frames without edits allocate nothing (deleting visible characters allocates one small
 * {@link Ghost} each, and moving the widget while ghosts are alive re-creates those ghosts).
 *
 * <p>Not thread-safe; use from the render thread only.
 */
public final class FieldAnimationState {
    /** Upper bound of simultaneously kept ghosts; further deletions simply vanish. */
    public static final int MAX_GHOSTS = 512;

    static final long NO_BIRTH = Long.MIN_VALUE;
    private static final long NEVER = Long.MIN_VALUE;
    static final float SNAP_EPSILON = 0.01f;
    static final long MAX_DT_MS = 100L;
    /** A char whose layout y moved more than this (line change in multi-line fields) snaps to its target x. */
    static final float LINE_CHANGE_EPSILON = 0.5f;
    /**
     * A removed char whose appear alpha was below this when it was last drawn was invisible (e.g. a staggered
     * char not revealed yet) and leaves no ghost.
     */
    static final float MIN_GHOST_ALPHA = 1f / 255f;
    /** Height of the glyph cell that {@link CharTransform#pivotY} is a fraction of. */
    static final float LINE_HEIGHT = 9f;
    private static final int MIN_CAPACITY = 16;
    private static final TypingConfig FALLBACK = new TypingConfig();

    // ------------------------------------------------ per-char arrays (index = UTF-16 index of current value)
    private int length;
    private long[] birth = new long[0];
    private float[] smoothX = new float[0];
    private float[] layY = new float[0];
    private float[] layW = new float[0];
    private int[] layCp = new int[0];
    private int[] layColor = new int[0];
    private Object[] layStyle = new Object[0];
    private long[] laidFrame = new long[0];
    private int[] seeds = new int[0];

    private String value;
    private boolean synced;
    private int seedCounter;

    // ------------------------------------------------ frame state
    private long frameId;
    private boolean frameOpen;
    private boolean hasFrame;
    private long nowMs;
    /** Time of the previous frame: chars laid out in the previous frame were drawn at this time. */
    private long prevNowMs;
    private float dtMs;
    private float originX, originY;
    private int scrollKey;
    private boolean snapThisFrame;
    /**
     * Origin delta of this frame (non-zero only on an origin change). Ghosts created this frame from the previous
     * frame's layout are shifted by it, like the ghosts that already existed.
     */
    private float ghostShiftX, ghostShiftY;
    /** False when the origin delta of this frame is not finite: the previous frame's layout can not be mapped. */
    private boolean ghostShiftValid = true;
    private boolean scrollChangedThisFrame;
    private long textChangedFrame = -1;
    private long idleFrame = -1;

    // ------------------------------------------------ smoothing parameters (from the last sync)
    private boolean animateThisFrame = true;
    private boolean reflowActive = true;
    private int glideMs = 55;
    private long alphaFrame = -1;
    private float alphaValue;

    private boolean unsettledThisFrame;
    private boolean unsettledLastFrame;

    private long latestBirth = NO_BIRTH;

    // ------------------------------------------------ ghosts
    private Ghost[] ghosts = new Ghost[0];
    private int ghostCount;
    private int removeDurationMs = 160;
    /** Scratch transform used to capture a removed char's appear pose (keeps ghost creation allocation-light). */
    private final CharTransform scratch = new CharTransform();

    // ------------------------------------------------ caret
    private boolean caretValid;
    private float caretSmooth;
    private long caretLastMs;
    private long caretFrame = -1;
    private boolean caretUnsettled;

    /**
     * Starts a frame. dt since the previous frame is clamped to 0..100 ms. The first frame and any change of the
     * origin snap all smoothing. On an origin change (the widget moved) the ghosts move with the text: existing
     * ghosts, and ghosts that {@link #sync} creates this frame from the previous frame's layout, are shifted by
     * the origin delta (dropped when that delta is not finite). {@code scrollKey} is e.g. the widget's display
     * position; a change only makes the frame non-idle.
     */
    public void beginFrame(long nowMs, float originX, float originY, int scrollKey) {
        if (frameOpen) {
            endFrame();
        }
        frameId++;
        frameOpen = true;
        ghostShiftX = 0f;
        ghostShiftY = 0f;
        ghostShiftValid = true;
        if (!hasFrame) {
            prevNowMs = nowMs;
            dtMs = 0f;
            snapThisFrame = true;
            scrollChangedThisFrame = false;
        } else {
            prevNowMs = this.nowMs;
            long d = nowMs - this.nowMs;
            dtMs = d <= 0L ? 0f : (float) Math.min(d, MAX_DT_MS);
            snapThisFrame = !sameFloat(originX, this.originX) || !sameFloat(originY, this.originY);
            scrollChangedThisFrame = scrollKey != this.scrollKey;
            if (snapThisFrame) {
                final float sx = originX - this.originX;
                final float sy = originY - this.originY;
                if (Float.isFinite(sx) && Float.isFinite(sy)) {
                    ghostShiftX = sx;
                    ghostShiftY = sy;
                    shiftGhosts(sx, sy);
                } else {
                    ghostShiftValid = false;
                    clearGhosts();
                }
            }
        }
        hasFrame = true;
        this.nowMs = nowMs;
        this.originX = originX;
        this.originY = originY;
        this.scrollKey = scrollKey;
        alphaFrame = -1;
    }

    /**
     * Diffs {@code value} against the previously synced value (common prefix/suffix on code point boundaries)
     * and keeps the per-char arrays aligned. Inserted chars get {@code birth = now + i*step} with
     * {@code step = n>1 ? min(staggerMs, maxStaggerMs/(n-1)) : 0} (i, n counted in code points); removed chars
     * that were laid out in the previous frame become ghosts that start from the pose the char was last drawn
     * with (a char removed mid-appear keeps its partial alpha/offset/scale; one that was still invisible, e.g. a
     * staggered char not revealed yet, leaves no ghost). {@code animate=false} or the very first sync:
     * no births/ghosts, and this frame's reflow/caret smoothing snaps to the targets (the field changes
     * instantly). A null value counts as "".
     */
    public void sync(String value, boolean animate, TypingConfig cfg) {
        if (cfg == null) cfg = FALLBACK;
        if (value == null) value = "";
        final boolean anim = animate && cfg.enabled;
        animateThisFrame = anim;
        boolean reflow = anim && cfg.smoothReflow && cfg.glideMs > 0;
        if (reflow != reflowActive || cfg.glideMs != glideMs) {
            alphaFrame = -1;
        }
        reflowActive = reflow;
        glideMs = cfg.glideMs;
        removeDurationMs = Math.max(1, cfg.removeDurationMs);
        if (!cfg.enabled || cfg.removeStyle == null || cfg.removeStyle == RemoveStyle.NONE) {
            clearGhosts();
        }
        if (!synced) {
            initChars(value.length());
            this.value = value;
            synced = true;
            textChangedFrame = frameId;
            return;
        }
        final String old = this.value;
        if (value == old || value.equals(old)) {
            return;
        }
        this.value = value;
        textChangedFrame = frameId;
        applyEdit(old, value, anim, cfg);
    }

    private void applyEdit(String old, String cur, boolean anim, TypingConfig cfg) {
        final int oldLen = old.length();
        final int newLen = cur.length();
        final int min = Math.min(oldLen, newLen);

        int p = 0;
        while (p < min && old.charAt(p) == cur.charAt(p)) p++;
        // never end the prefix between a high and a low surrogate
        if (p > 0 && Character.isHighSurrogate(old.charAt(p - 1))
                && ((p < oldLen && Character.isLowSurrogate(old.charAt(p)))
                || (p < newLen && Character.isLowSurrogate(cur.charAt(p))))) {
            p--;
        }
        int s = 0;
        final int maxS = min - p;
        while (s < maxS && old.charAt(oldLen - 1 - s) == cur.charAt(newLen - 1 - s)) s++;
        // never start the suffix between a high and a low surrogate
        if (s > 0) {
            int so = oldLen - s;
            int sn = newLen - s;
            if (Character.isLowSurrogate(old.charAt(so))
                    && ((so > 0 && Character.isHighSurrogate(old.charAt(so - 1)))
                    || (sn > 0 && Character.isHighSurrogate(cur.charAt(sn - 1))))) {
                s--;
            }
        }
        final int removed = oldLen - s - p;
        final int inserted = newLen - s - p;

        // removed chars that were visible last frame -> ghosts
        if (anim && removed > 0 && cfg.removeStyle != null && cfg.removeStyle != RemoveStyle.NONE) {
            for (int i = p, end = p + removed; i < end; i++) {
                final long lf = laidFrame[i];
                if (lf == frameId) {
                    // laid out earlier in this very frame (second sync): current coordinates and time
                    addGhost(i, nowMs, 0f, 0f, cfg);
                } else if (lf == frameId - 1 && ghostShiftValid) {
                    // laid out last frame: drawn at the previous frame's time, in the previous frame's coordinates
                    addGhost(i, prevNowMs, ghostShiftX, ghostShiftY, cfg);
                }
            }
        }

        // shift the common suffix so every char keeps its identity
        ensureCapacity(newLen);
        final int from = p + removed;
        final int to = p + inserted;
        if (s > 0 && from != to) {
            System.arraycopy(birth, from, birth, to, s);
            System.arraycopy(smoothX, from, smoothX, to, s);
            System.arraycopy(layY, from, layY, to, s);
            System.arraycopy(layW, from, layW, to, s);
            System.arraycopy(layCp, from, layCp, to, s);
            System.arraycopy(layColor, from, layColor, to, s);
            System.arraycopy(layStyle, from, layStyle, to, s);
            System.arraycopy(laidFrame, from, laidFrame, to, s);
            System.arraycopy(seeds, from, seeds, to, s);
        }
        if (newLen < oldLen) {
            Arrays.fill(layStyle, newLen, oldLen, null);
        }
        length = newLen;

        // fresh identities for inserted chars
        final boolean births = anim && inserted > 0 && cfg.appearStyle != null && cfg.appearStyle != AppearStyle.NONE;
        final int n = births ? cur.codePointCount(p, to) : 0;
        final float step = n > 1
                ? Math.min((float) Math.max(0, cfg.staggerMs), (float) Math.max(0, cfg.maxStaggerMs) / (n - 1))
                : 0f;
        int cpIndex = 0;
        for (int i = p; i < to; i++) {
            if (births) {
                boolean lowHalf = i > p && Character.isLowSurrogate(cur.charAt(i))
                        && Character.isHighSurrogate(cur.charAt(i - 1));
                if (lowHalf) {
                    birth[i] = birth[i - 1];
                } else {
                    birth[i] = nowMs + Math.round(cpIndex * step);
                    cpIndex++;
                }
            } else {
                birth[i] = NO_BIRTH;
            }
            initSlot(i);
        }
        if (births) {
            long last = nowMs + Math.round((n - 1) * step);
            if (latestBirth == NO_BIRTH || last > latestBirth) {
                latestBirth = last;
            }
        }
    }

    /**
     * Records the layout of char {@code index} (UTF-16 index of its first unit) for this frame and returns the
     * x to draw it at: the smoothed x, or {@code targetX} itself when smoothing is off/settled, for new chars,
     * chars not laid out in the previous frame, on snap frames (first frame, origin change), on idle frames,
     * and when the char moved to another line (y changed).
     */
    public float layoutChar(int index, float targetX, float y, float width, int codepoint, Object style, int color) {
        if (index < 0 || index >= length) {
            return targetX;
        }
        final long lf = laidFrame[index];
        final boolean sameFrame = lf == frameId;
        float x;
        if (!reflowActive || snapThisFrame || idleFrame == frameId || !(sameFrame || lf == frameId - 1)
                || !Float.isFinite(targetX) || !Float.isFinite(smoothX[index])
                || Math.abs(y - layY[index]) > LINE_CHANGE_EPSILON) {
            x = targetX;
        } else {
            float cur = smoothX[index];
            float a = sameFrame ? 0f : frameAlpha();
            x = cur + (targetX - cur) * a;
            // A step that makes no progress means the float increment rounded away (tiny alpha at high fps, large
            // x): stop there instead of stalling a few hundredths of a pixel off target forever.
            if (!(Math.abs(targetX - x) >= SNAP_EPSILON) || (a > 0f && x == cur)) {
                x = targetX;
            }
        }
        if (x != targetX && Float.isFinite(targetX)) {
            unsettledThisFrame = true;
        }
        smoothX[index] = x;
        layY[index] = y;
        layW[index] = width;
        layCp[index] = codepoint;
        layStyle[index] = style;
        layColor[index] = color;
        laidFrame[index] = frameId;
        return x;
    }

    /** Appear transform of char {@code index} at the current frame time; false (and identity in out) when none. */
    public boolean appearTransform(int index, TypingConfig cfg, CharTransform out) {
        out.reset();
        if (cfg == null) cfg = FALLBACK;
        if (index < 0 || index >= length) {
            return false;
        }
        return appearAt(index, nowMs, cfg, out, true);
    }

    /**
     * Appear transform of char {@code index} (in range) at time {@code atMs}; false (and identity in out) when
     * none. With {@code forgetFinished} a finished birth is cleared.
     */
    private boolean appearAt(int index, long atMs, TypingConfig cfg, CharTransform out, boolean forgetFinished) {
        out.reset();
        if (!cfg.enabled) {
            return false;
        }
        final long b = birth[index];
        final AppearStyle style = cfg.appearStyle;
        if (b == NO_BIRTH || style == null || style == AppearStyle.NONE) {
            return false;
        }
        final int dur = Math.max(1, cfg.durationMs);
        final float t = (float) ((double) (atMs - b) / dur);
        if (t >= 1f) {
            if (forgetFinished) {
                birth[index] = NO_BIRTH;
            }
            return false;
        }
        final Easing easing = cfg.easing == null ? Easing.AUTO : cfg.easing;
        final float e = t <= 0f ? 0f : easing.resolve(style.defaultEasing()).apply(t);
        style.apply(t, e, cfg.intensity, seeds[index], atMs, out);
        return !out.isIdentity();
    }

    /** Number of ghosts currently kept (some may already be expired until the next {@link #endFrame()}). */
    public int ghostCount() {
        return ghostCount;
    }

    /** Ghost {@code i} (0 &lt;= i &lt; ghostCount()), or null when out of range. */
    public Ghost ghost(int i) {
        return i >= 0 && i < ghostCount ? ghosts[i] : null;
    }

    /**
     * Exit transform of a ghost; false (alpha 0 in out) when it is expired or invisible. The exit starts from the
     * pose the char was last drawn with: a char removed while still appearing keeps that partial pose (alpha
     * multiplied in; offset, scale and rotation held, expressed around the cell centre), and a SCRAMBLE char
     * removed before its reveal keeps its scrambled glyph in {@code out.glyph}. Draw {@code out.glyph} instead of
     * {@link Ghost#codepoint} when it is not -1, as for appear transforms.
     */
    public boolean ghostTransform(Ghost g, TypingConfig cfg, CharTransform out) {
        out.reset();
        if (cfg == null) cfg = FALLBACK;
        final RemoveStyle style = cfg.removeStyle;
        if (g == null || !cfg.enabled || style == null || style == RemoveStyle.NONE) {
            out.alpha = 0f;
            return false;
        }
        final int dur = Math.max(1, cfg.removeDurationMs);
        removeDurationMs = dur;
        float t = (float) ((double) (nowMs - g.removedAt) / dur);
        if (!(t < 1f)) {
            out.alpha = 0f;
            return false;
        }
        if (t < 0f) {
            t = 0f;
        }
        final Easing easing = cfg.easing == null ? Easing.AUTO : cfg.easing;
        final float e = easing.resolve(style.defaultEasing()).apply(t);
        style.apply(t, e, cfg.intensity, g.seed, out);
        if (g.hasStartPose) {
            // every RemoveStyle pivots around the cell centre, like the stored start pose
            out.dx += g.startDx;
            out.dy += g.startDy;
            out.scaleX *= g.startScaleX;
            out.scaleY *= g.startScaleY;
            out.rotation += g.startRotation;
            out.alpha *= g.startAlpha;
            if (g.startGlyph != -1) {
                out.glyph = g.startGlyph;
            }
            out.sanitize();
        }
        return out.alpha > 0f;
    }

    /**
     * Smoothed caret x (exponential smoothing with the caret's own dt, clamped to 0..100 ms). Returns
     * {@code targetX} when smoothCursor is off, glideMs is 0, the field is not animated this frame, on the first
     * call and on snap frames. Call it after {@link #sync} on every frame in which the field shows a caret,
     * including idle frames (see the class comment): preferably before {@link #isIdle}, so that a caret move
     * starting this frame makes the frame non-idle. Idle frames do not snap the caret. Further calls in the same
     * frame do not advance the glide.
     */
    public float caretX(float targetX, TypingConfig cfg) {
        if (cfg == null) cfg = FALLBACK;
        float x;
        final boolean smooth = cfg.enabled && cfg.smoothCursor && cfg.glideMs > 0 && animateThisFrame && caretValid
                && !snapThisFrame && Float.isFinite(targetX) && Float.isFinite(caretSmooth);
        if (!smooth) {
            x = targetX;
        } else if (caretFrame == frameId) {
            x = caretSmooth;
            if (!(Math.abs(targetX - x) >= SNAP_EPSILON)) x = targetX;
        } else {
            long d = nowMs - caretLastMs;
            float dt = d <= 0L ? 0f : (float) Math.min(d, MAX_DT_MS);
            float a = (float) (1.0 - Math.exp(-dt / (double) cfg.glideMs));
            x = caretSmooth + (targetX - caretSmooth) * a;
            // no progress = the float increment rounded away: stop instead of stalling off target (see layoutChar)
            if (!(Math.abs(targetX - x) >= SNAP_EPSILON) || (a > 0f && x == caretSmooth)) x = targetX;
        }
        caretSmooth = x;
        caretValid = true;
        caretLastMs = nowMs;
        caretFrame = frameId;
        caretUnsettled = x != targetX && Float.isFinite(targetX);
        return x;
    }

    /**
     * True when the <em>text</em> does not animate: no active births, no live ghosts, all chars laid out last
     * frame (and so far this frame) settled, and text, scrollKey and origin unchanged this frame; also the caret
     * as far as known: the most recent {@link #caretX} call left it settled (a caret not queried this or last
     * frame counts as settled). Always true when {@code cfg.enabled} is false. When true, this frame's
     * {@link #layoutChar} calls snap to their targets and the renderer may draw the text the vanilla way.
     *
     * <p>The caret is covered for this frame only if {@link #caretX} was already called this frame. Otherwise a
     * caret move that starts in this frame is not seen here, so the idle fast path must never bypass
     * {@code caretX}: the renderer draws the caret at {@code caretX}'s result on idle frames too. The glide that
     * {@code caretX} then starts makes the following frames non-idle until it settles.
     */
    public boolean isIdle(TypingConfig cfg) {
        if (cfg == null) cfg = FALLBACK;
        final boolean idle;
        if (!cfg.enabled) {
            idle = true;
        } else {
            // a caret that was not queried this or last frame is not drawn (unfocused/blink) -> not animating
            boolean caretBusy = caretUnsettled && caretFrame >= frameId - 1;
            idle = frameId > 0 && !snapThisFrame && !scrollChangedThisFrame && textChangedFrame != frameId
                    && !unsettledLastFrame && !unsettledThisFrame && !caretBusy
                    && !birthsActive(cfg) && !ghostsActive(cfg);
        }
        if (idle) {
            idleFrame = frameId;
        }
        return idle;
    }

    /** Ends the frame: prunes expired ghosts and rolls the per-frame "settled" bookkeeping. */
    public void endFrame() {
        if (frameOpen) {
            frameOpen = false;
            unsettledLastFrame = unsettledThisFrame;
            unsettledThisFrame = false;
        }
        pruneGhosts();
    }

    /** Forgets everything; the next {@link #sync} is a first sync and the next frame snaps. */
    public void reset() {
        Arrays.fill(layStyle, null);
        Arrays.fill(birth, NO_BIRTH);
        Arrays.fill(laidFrame, NEVER);
        length = 0;
        value = null;
        synced = false;
        clearGhosts();
        hasFrame = false;
        frameOpen = false;
        snapThisFrame = false;
        scrollChangedThisFrame = false;
        ghostShiftX = 0f;
        ghostShiftY = 0f;
        ghostShiftValid = true;
        textChangedFrame = -1;
        idleFrame = -1;
        unsettledThisFrame = false;
        unsettledLastFrame = false;
        latestBirth = NO_BIRTH;
        caretValid = false;
        caretUnsettled = false;
        caretFrame = -1;
        animateThisFrame = true;
        alphaFrame = -1;
    }

    // ------------------------------------------------------------------ internals

    private boolean birthsActive(TypingConfig cfg) {
        if (latestBirth == NO_BIRTH || cfg.appearStyle == null || cfg.appearStyle == AppearStyle.NONE) {
            return false;
        }
        final int dur = Math.max(1, cfg.durationMs);
        if (nowMs - latestBirth < dur) {
            for (int i = 0; i < length; i++) {
                long b = birth[i];
                if (b != NO_BIRTH && nowMs - b < dur) {
                    return true;
                }
            }
        }
        // every birth is finished: forget them so a later duration change can not restart them
        Arrays.fill(birth, 0, length, NO_BIRTH);
        latestBirth = NO_BIRTH;
        return false;
    }

    private boolean ghostsActive(TypingConfig cfg) {
        if (ghostCount == 0 || cfg.removeStyle == null || cfg.removeStyle == RemoveStyle.NONE) {
            return false;
        }
        final int dur = Math.max(1, cfg.removeDurationMs);
        for (int i = 0; i < ghostCount; i++) {
            if (nowMs - ghosts[i].removedAt < dur) {
                return true;
            }
        }
        return false;
    }

    private float frameAlpha() {
        if (alphaFrame != frameId) {
            alphaValue = glideMs <= 0 ? 1f : (float) (1.0 - Math.exp(-dtMs / (double) glideMs));
            alphaFrame = frameId;
        }
        return alphaValue;
    }

    private void initChars(int n) {
        ensureCapacity(n);
        Arrays.fill(layStyle, null);
        length = n;
        for (int i = 0; i < n; i++) {
            birth[i] = NO_BIRTH;
            initSlot(i);
        }
    }

    private void initSlot(int i) {
        smoothX[i] = 0f;
        layY[i] = 0f;
        layW[i] = 0f;
        layCp[i] = 0;
        layColor[i] = 0;
        layStyle[i] = null;
        laidFrame[i] = NEVER;
        seeds[i] = Mix.mix(++seedCounter * 0x9E3779B9);
    }

    private void ensureCapacity(int n) {
        final int cap = birth.length;
        if (n <= cap) {
            return;
        }
        int newCap = Math.max(Math.max(n, MIN_CAPACITY), cap * 2);
        if (newCap < 0) newCap = n; // overflow guard
        birth = Arrays.copyOf(birth, newCap);
        Arrays.fill(birth, cap, newCap, NO_BIRTH);
        smoothX = Arrays.copyOf(smoothX, newCap);
        layY = Arrays.copyOf(layY, newCap);
        layW = Arrays.copyOf(layW, newCap);
        layCp = Arrays.copyOf(layCp, newCap);
        layColor = Arrays.copyOf(layColor, newCap);
        layStyle = Arrays.copyOf(layStyle, newCap);
        laidFrame = Arrays.copyOf(laidFrame, newCap);
        Arrays.fill(laidFrame, cap, newCap, NEVER);
        seeds = Arrays.copyOf(seeds, newCap);
    }

    /**
     * Turns removed char {@code i} into a ghost. {@code drawnAtMs} is the time it was last drawn: its appear pose
     * at that time becomes the ghost's start pose (an invisible char gets no ghost). {@code shiftX/Y} maps its
     * recorded layout into this frame's coordinates.
     */
    private void addGhost(int i, long drawnAtMs, float shiftX, float shiftY, TypingConfig cfg) {
        if (ghostCount >= MAX_GHOSTS) {
            return;
        }
        boolean pose = false;
        float alpha0 = 1f, dx0 = 0f, dy0 = 0f, sx0 = 1f, sy0 = 1f, rot0 = 0f;
        int glyph0 = -1;
        final CharTransform a = scratch;
        if (appearAt(i, drawnAtMs, cfg, a, false)) {
            if (!(a.alpha >= MIN_GHOST_ALPHA)) {
                return; // it was (practically) invisible: nothing to animate out
            }
            pose = true;
            alpha0 = a.alpha;
            sx0 = a.scaleX;
            sy0 = a.scaleY;
            rot0 = a.rotation;
            glyph0 = a.glyph;
            // Re-express the pose around the cell centre (the pivot of every RemoveStyle). Drawing p + R*S*(q - p)
            // equals c + R*S*(q - c) + (v - R*S*v) with v = p - c.
            final float w = Float.isFinite(layW[i]) ? layW[i] : 0f;
            final float vx = (a.pivotX - 0.5f) * w;
            final float vy = (a.pivotY - 0.5f) * LINE_HEIGHT;
            float rsx = sx0 * vx;
            float rsy = sy0 * vy;
            if (rot0 != 0f) {
                final float cos = (float) Math.cos(rot0);
                final float sin = (float) Math.sin(rot0);
                final float rx = cos * rsx - sin * rsy;
                final float ry = sin * rsx + cos * rsy;
                rsx = rx;
                rsy = ry;
            }
            dx0 = a.dx + vx - rsx;
            dy0 = a.dy + vy - rsy;
        }
        if (ghostCount == ghosts.length) {
            ghosts = Arrays.copyOf(ghosts, Math.min(MAX_GHOSTS, Math.max(8, ghostCount * 2)));
        }
        ghosts[ghostCount++] = new Ghost(layCp[i], layStyle[i], layColor[i], smoothX[i] + shiftX, layY[i] + shiftY,
                layW[i], nowMs, seeds[i], pose, alpha0, dx0, dy0, sx0, sy0, rot0, glyph0);
    }

    /** Moves every ghost by (dx, dy) (the widget moved). */
    private void shiftGhosts(float dx, float dy) {
        for (int k = 0; k < ghostCount; k++) {
            ghosts[k] = ghosts[k].shifted(dx, dy);
        }
    }

    private void pruneGhosts() {
        int w = 0;
        for (int r = 0; r < ghostCount; r++) {
            Ghost g = ghosts[r];
            if (nowMs - g.removedAt < removeDurationMs) {
                ghosts[w++] = g;
            }
        }
        for (int i = w; i < ghostCount; i++) {
            ghosts[i] = null;
        }
        ghostCount = w;
    }

    private void clearGhosts() {
        for (int i = 0; i < ghostCount; i++) {
            ghosts[i] = null;
        }
        ghostCount = 0;
    }

    private static boolean sameFloat(float a, float b) {
        return a == b || (a != a && b != b);
    }

    // ------------------------------------------------------------------ test hooks (package-private)

    int length() {
        return length;
    }

    long birthAt(int index) {
        return birth[index];
    }

    float smoothedXAt(int index) {
        return smoothX[index];
    }

    String syncedValue() {
        return value;
    }

    /**
     * A deleted character that is still animating out, drawn at its last recorded layout. It also remembers the
     * appear pose it was last drawn with (applied by {@link #ghostTransform}); a ghost made with the public
     * constructor starts fully visible.
     */
    public static final class Ghost {
        public final int codepoint;
        public final Object style;
        public final int color;
        public final float x, y, width;
        public final long removedAt;
        public final int seed;

        /** Start pose (appear transform when last drawn, re-expressed around the cell centre); see ghostTransform. */
        final boolean hasStartPose;
        final float startAlpha, startDx, startDy, startScaleX, startScaleY, startRotation;
        final int startGlyph;

        public Ghost(int codepoint, Object style, int color, float x, float y, float width, long removedAt, int seed) {
            this(codepoint, style, color, x, y, width, removedAt, seed, false, 1f, 0f, 0f, 1f, 1f, 0f, -1);
        }

        Ghost(int codepoint, Object style, int color, float x, float y, float width, long removedAt, int seed,
              boolean hasStartPose, float startAlpha, float startDx, float startDy, float startScaleX,
              float startScaleY, float startRotation, int startGlyph) {
            this.codepoint = codepoint;
            this.style = style;
            this.color = color;
            this.x = x;
            this.y = y;
            this.width = width;
            this.removedAt = removedAt;
            this.seed = seed;
            this.hasStartPose = hasStartPose;
            this.startAlpha = startAlpha;
            this.startDx = startDx;
            this.startDy = startDy;
            this.startScaleX = startScaleX;
            this.startScaleY = startScaleY;
            this.startRotation = startRotation;
            this.startGlyph = startGlyph;
        }

        /** Copy moved by (dx, dy). */
        Ghost shifted(float dx, float dy) {
            return new Ghost(codepoint, style, color, x + dx, y + dy, width, removedAt, seed, hasStartPose, startAlpha,
                    startDx, startDy, startScaleX, startScaleY, startRotation, startGlyph);
        }

        @Override
        public String toString() {
            return "Ghost{cp=" + codepoint + ", x=" + x + ", y=" + y + ", w=" + width + ", removedAt=" + removedAt
                    + (hasStartPose ? ", startAlpha=" + startAlpha + ", startDx=" + startDx + ", startDy=" + startDy
                    + ", startScale=" + startScaleX + "x" + startScaleY + ", startRot=" + startRotation
                    + ", startGlyph=" + startGlyph : "") + '}';
        }
    }
}
