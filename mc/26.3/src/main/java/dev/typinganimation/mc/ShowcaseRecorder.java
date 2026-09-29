package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frame recorder of the dev showcase ({@link Showcase}): a deterministic animation clock and the read-back of every
 * rendered frame of a clip into {@code <out>/<clip>/frame-NNNNN.png}.
 *
 * <p>While a clip is recorded the clock (installed as the {@link AnimationClock} override) advances by exactly one
 * frame interval ({@link #FRAME_MS}) per captured frame, whatever the real frame time is; between clips it follows
 * the real clock from where the last clip left it (monotonic). Captured frames are also paced to at least one frame
 * interval of real time, so the game (which ticks in real time) runs at the recorded speed too.
 */
final class ShowcaseRecorder {
    /** Frame interval of the recording (30 fps). */
    static final double FRAME_MS = 1000.0 / 30.0;
    private static final int MAX_QUEUED_WRITES = 24;

    private final Minecraft mc;
    private final Path out;
    private final ExecutorService writer = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "typinganimation-showcase-writer");
        t.setDaemon(true);
        return t;
    });
    /** Frames requested and not yet written (GPU read-back pending or PNG being written). */
    private final AtomicInteger pending = new AtomicInteger();
    /** Frames handed to the PNG writers and not yet written. */
    private final AtomicInteger writing = new AtomicInteger();
    private final Map<Integer, String> stills = new HashMap<>();
    private volatile Throwable error;

    private boolean capturing;
    private String clip;
    private int frame;
    private long base;
    private long anchorClock;
    private long anchorReal;
    private long lastFrameNanos;

    ShowcaseRecorder(Minecraft mc, Path out) {
        this.mc = mc;
        this.out = out;
        anchorReal = Util.getMillis();
        anchorClock = anchorReal;
    }

    Path out() {
        return out;
    }

    /** Showcase clock (ms): virtual while recording, real time (continued monotonically) otherwise. */
    long now() {
        return capturing ? base + Math.round(frame * FRAME_MS) : anchorClock + (Util.getMillis() - anchorReal);
    }

    boolean capturing() {
        return capturing;
    }

    /** Frames captured so far in the current (or last) clip. */
    int frames() {
        return frame;
    }

    Throwable error() {
        return error;
    }

    /** Frames requested and not yet on disk. */
    int pending() {
        return pending.get();
    }

    /** Starts clip {@code name}: the next rendered frame is its frame 0 (old frames of that clip are deleted). */
    void start(String name) throws IOException {
        Path dir = out.resolve(name);
        Files.createDirectories(dir);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "frame-*.png")) {
            for (Path p : old) {
                Files.delete(p);
            }
        }
        base = now();
        frame = 0;
        clip = name;
        stills.clear();
        capturing = true;
        lastFrameNanos = System.nanoTime();
    }

    /** Ends the clip; the clock continues in real time from the clip's end. Returns the number of frames. */
    int stop() {
        long t = now();
        capturing = false;
        anchorClock = t;
        anchorReal = Util.getMillis();
        return frame;
    }

    /** The next captured frame is also saved as {@code <out>/stills/<name>.png}. */
    void markStill(String name) {
        stills.put(frame, name);
    }

    /**
     * End of a rendered frame: while recording, reads the frame back (asynchronously) as the clip's next frame,
     * advances the clock by one frame interval and paces the recording.
     */
    void onFrame() {
        if (!capturing) {
            return;
        }
        int index = frame;
        String still = stills.remove(index);
        grab(out.resolve(clip).resolve(String.format(Locale.ROOT, "frame-%05d.png", index)), still);
        frame++;
        pace();
    }

    private void grab(Path file, String still) {
        pending.incrementAndGet();
        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                writing.incrementAndGet();
                try {
                    writer.execute(() -> write(image, file, still));
                } catch (Throwable t) {
                    image.close();
                    writing.decrementAndGet();
                    pending.decrementAndGet();
                    error = t;
                }
            });
        } catch (Throwable t) {
            pending.decrementAndGet();
            throw t;
        }
    }

    private void write(NativeImage image, Path file, String still) {
        try {
            image.writeToFile(file);
            if (still != null) {
                Path dir = out.resolve("stills");
                Files.createDirectories(dir);
                Files.copy(file, dir.resolve(still + ".png"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            error = t;
        } finally {
            image.close();
            writing.decrementAndGet();
            pending.decrementAndGet();
        }
    }

    /** At least one frame interval of real time per captured frame, and a short PNG write queue. */
    private void pace() {
        long target = lastFrameNanos + (long) (FRAME_MS * 1_000_000.0);
        while (System.nanoTime() < target || writing.get() > MAX_QUEUED_WRITES) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        lastFrameNanos = System.nanoTime();
    }

    /** Stops the PNG writers (after the pending frames were written). */
    void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
