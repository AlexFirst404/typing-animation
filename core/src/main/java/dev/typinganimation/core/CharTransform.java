package dev.typinganimation.core;

/**
 * Mutable per-character transform written by {@link AppearStyle} / {@link RemoveStyle}. Reuse one instance
 * per renderer; nothing here allocates.
 */
public final class CharTransform {
    /** GUI px offset. */
    public float dx, dy;
    public float scaleX = 1, scaleY = 1;
    /** Radians, positive = clockwise on screen. */
    public float rotation;
    /** 0..1 multiplier of the text colour alpha. */
    public float alpha = 1;
    /** Pivot as fraction of the glyph cell (width x 9 px line). */
    public float pivotX = 0.5f, pivotY = 0.5f;
    /** Code point override (SCRAMBLE), -1 = none. */
    public int glyph = -1;

    /** Resets to identity. */
    public void reset() {
        dx = 0f;
        dy = 0f;
        scaleX = 1f;
        scaleY = 1f;
        rotation = 0f;
        alpha = 1f;
        pivotX = 0.5f;
        pivotY = 0.5f;
        glyph = -1;
    }

    /** True when drawing with this transform equals drawing plainly (the pivot is irrelevant then). */
    public boolean isIdentity() {
        return dx == 0f && dy == 0f && scaleX == 1f && scaleY == 1f && rotation == 0f && alpha == 1f && glyph == -1;
    }

    /** Extra helper: true when a scale or rotation is present (a translate-only draw is not enough). */
    public boolean hasScaleOrRotation() {
        return scaleX != 1f || scaleY != 1f || rotation != 0f;
    }

    /** Extra helper: copies every field from {@code other}. */
    public void set(CharTransform other) {
        dx = other.dx;
        dy = other.dy;
        scaleX = other.scaleX;
        scaleY = other.scaleY;
        rotation = other.rotation;
        alpha = other.alpha;
        pivotX = other.pivotX;
        pivotY = other.pivotY;
        glyph = other.glyph;
    }

    /**
     * Extra helper: replaces non-finite values by their identity value, clamps alpha to [0,1] and scales to
     * be non-negative, and drops invalid glyph overrides. The styles call this themselves.
     */
    public void sanitize() {
        if (!Float.isFinite(dx)) dx = 0f;
        if (!Float.isFinite(dy)) dy = 0f;
        if (!Float.isFinite(scaleX)) scaleX = 1f;
        if (!Float.isFinite(scaleY)) scaleY = 1f;
        if (scaleX < 0f) scaleX = 0f;
        if (scaleY < 0f) scaleY = 0f;
        if (!Float.isFinite(rotation)) rotation = 0f;
        alpha = Float.isNaN(alpha) ? 1f : Mix.clamp01(alpha);
        if (!Float.isFinite(pivotX)) pivotX = 0.5f;
        if (!Float.isFinite(pivotY)) pivotY = 0.5f;
        if (glyph != -1 && !Character.isValidCodePoint(glyph)) glyph = -1;
    }

    @Override
    public String toString() {
        return "CharTransform{dx=" + dx + ", dy=" + dy + ", scale=" + scaleX + "x" + scaleY + ", rot=" + rotation
                + ", alpha=" + alpha + ", pivot=" + pivotX + "," + pivotY + ", glyph=" + glyph + '}';
    }
}
