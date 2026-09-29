import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Turns a PNG frame sequence into an optimised, looping GIF. Dependency-free (JDK only); run it as a single-file
 * program:
 *
 * <pre>
 * java tools/MakeGif.java --in DIR --out FILE.gif [--crop X,Y,W,H] [--scale N] [--delay MS] [--from I] [--to I]
 *                         [--hold-last MS] [--max-bytes N] [--max-width PX]
 * </pre>
 *
 * <ul>
 *   <li>Frames are the {@code *.png} files of {@code --in} in name order; {@code --from}/{@code --to} pick a range
 *       (indices into that list, inclusive).</li>
 *   <li>{@code --crop} is applied first (source pixels), then {@code --scale} (integer, nearest neighbour).</li>
 *   <li>{@code --delay} is the fixed frame interval in ms (default 33.333 = 30 fps). GIF delays are centiseconds, so
 *       each frame gets the rounded share that keeps the running total exact (33.333 ms: 3, 3, 4, 3, 3, 4 cs...).
 *       Identical consecutive frames are merged into one longer frame. {@code --hold-last} adds a pause before the
 *       loop restarts.</li>
 *   <li>One global palette for the whole animation, no dithering (flat colours and text stay crisp): an exact palette
 *       when there are at most 255 colours; otherwise the most frequent colours are kept exactly and the rest is
 *       median-cut, refined with k-means. Index 255 is reserved for transparency.</li>
 *   <li>Every frame after the first stores only the rectangle that changed, with unchanged pixels transparent
 *       (disposal "do not dispose"), which keeps typing clips small. Loops forever.</li>
 *   <li>Fails (exit code 1) when the GIF is larger than {@code --max-bytes} (default 4,000,000) or wider than
 *       {@code --max-width} (default 800).</li>
 * </ul>
 */
public final class MakeGif {
    private static final int TRANSPARENT = 255;
    private static final int MAX_COLORS = 255;

    public static void main(String[] args) throws Exception {
        Options o = Options.parse(args);
        List<Path> files;
        try (Stream<Path> s = Files.list(o.in)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no .png frames in " + o.in);
        }
        int from = Math.max(0, o.from);
        int to = o.to < 0 ? files.size() - 1 : Math.min(o.to, files.size() - 1);
        if (from > to) {
            throw new IllegalArgumentException("empty frame range " + from + ".." + to + " of " + files.size());
        }
        files = files.subList(from, to + 1);
        long t0 = System.nanoTime();

        // pass 1: decode (parallel), crop, scale; histogram
        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int[][] frames = new int[files.size()][];
        int[] size = new int[2];
        try {
            List<Future<int[]>> jobs = new ArrayList<>();
            for (Path f : files) {
                jobs.add(pool.submit(() -> load(f, o, size)));
            }
            for (int i = 0; i < jobs.size(); i++) {
                frames[i] = jobs.get(i).get();
            }
        } finally {
            pool.shutdown();
        }
        int w = size[0];
        int h = size[1];
        if (w > o.maxWidth) {
            fail("the GIF would be " + w + " px wide (more than --max-width " + o.maxWidth + ")");
        }
        ColorCounts counts = new ColorCounts();
        for (int[] frame : frames) {
            for (int c : frame) {
                counts.add(c & 0xFFFFFF);
            }
        }
        int[] palette = Palette.build(counts);
        IndexMap index = new IndexMap(counts, palette);

        // pass 2: quantise, diff against the previous displayed frame, encode
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
        GifWriter gif = new GifWriter(bytes, w, h, palette);
        byte[] shown = null;
        byte[] pending = null;
        int[] pendingRect = null;
        long pendingDelayMs10 = 0;   // in 1/10 ms units to keep the fixed interval exact
        double delay10 = o.delayMs * 10.0;
        long totalCs = 0;
        long elapsed10 = 0;
        int written = 0;
        for (int i = 0; i < frames.length; i++) {
            byte[] q = new byte[w * h];
            int[] src = frames[i];
            for (int p = 0; p < q.length; p++) {
                q[p] = (byte) index.get(src[p] & 0xFFFFFF);
            }
            frames[i] = null;
            int[] rect = shown == null ? new int[]{0, 0, w, h} : changedRect(shown, q, w, h);
            if (rect == null) {
                pendingDelayMs10 += Math.round(delay10); // identical to the previous frame: extend it
                continue;
            }
            if (pending != null) {
                long cs = centis(elapsed10, pendingDelayMs10);
                elapsed10 += pendingDelayMs10;
                totalCs += cs;
                gif.frame(pending, pendingRect, (int) cs);
                written++;
            }
            pending = subImage(shown, q, w, rect);
            pendingRect = rect;
            pendingDelayMs10 = Math.round(delay10);
            shown = q;
        }
        long lastDelay10 = pendingDelayMs10 + Math.round(o.holdLastMs * 10.0);
        long cs = centis(elapsed10, lastDelay10);
        totalCs += cs;
        gif.frame(pending, pendingRect, (int) cs);
        written++;
        gif.finish();

        byte[] out = bytes.toByteArray();
        Files.createDirectories(o.out.toAbsolutePath().getParent());
        Files.write(o.out, out);
        System.out.printf(Locale.ROOT, "%s: %dx%d, %d source frames -> %d GIF frames, %.2f s, %d colours in -> %d"
                        + " palette entries, %,d bytes (%.1f s)%n", o.out, w, h, files.size(), written, totalCs / 100.0,
                counts.size(), palette.length, out.length, (System.nanoTime() - t0) / 1e9);
        if (out.length > o.maxBytes) {
            fail(out.length + " bytes is more than --max-bytes " + o.maxBytes);
        }
    }

    private static void fail(String message) {
        System.err.println("MakeGif: " + message);
        System.exit(1);
    }

    /** Centiseconds for a frame that starts at {@code start10} and lasts {@code len10} (1/10 ms): exact running total. */
    private static long centis(long start10, long len10) {
        long cs = Math.round((start10 + len10) / 100.0) - Math.round(start10 / 100.0);
        return Math.max(2, cs); // browsers slow down delays below 2 cs
    }

    private static int[] load(Path file, Options o, int[] size) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            throw new IOException("not an image: " + file);
        }
        int x = 0;
        int y = 0;
        int cw = img.getWidth();
        int ch = img.getHeight();
        if (o.crop != null) {
            x = Math.max(0, o.crop[0]);
            y = Math.max(0, o.crop[1]);
            cw = Math.min(o.crop[2], img.getWidth() - x);
            ch = Math.min(o.crop[3], img.getHeight() - y);
            if (cw <= 0 || ch <= 0) {
                throw new IllegalArgumentException("crop outside the " + img.getWidth() + "x" + img.getHeight()
                        + " frame " + file);
            }
        }
        int[] px = img.getRGB(x, y, cw, ch, null, 0, cw);
        int s = o.scale;
        int w = cw * s;
        int h = ch * s;
        synchronized (size) {
            if (size[0] == 0) {
                size[0] = w;
                size[1] = h;
            } else if (size[0] != w || size[1] != h) {
                throw new IllegalArgumentException("frame size differs: " + file);
            }
        }
        if (s == 1) {
            return px;
        }
        int[] scaled = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            int row = (yy / s) * cw;
            for (int xx = 0; xx < w; xx++) {
                scaled[yy * w + xx] = px[row + xx / s];
            }
        }
        return scaled;
    }

    /** Bounding box {x, y, w, h} of the pixels that differ, or null when the frames are identical. */
    private static int[] changedRect(byte[] a, byte[] b, int w, int h) {
        int x0 = w;
        int y0 = h;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (a[row + x] != b[row + x]) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    y1 = y;
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1};
    }

    /** The rectangle of {@code cur}; pixels equal to the previous frame become transparent. */
    private static byte[] subImage(byte[] prev, byte[] cur, int w, int[] r) {
        byte[] out = new byte[r[2] * r[3]];
        int k = 0;
        for (int y = r[1]; y < r[1] + r[3]; y++) {
            for (int x = r[0]; x < r[0] + r[2]; x++) {
                int p = y * w + x;
                out[k++] = prev != null && prev[p] == cur[p] ? (byte) TRANSPARENT : cur[p];
            }
        }
        return out;
    }

    // ================================================================== options

    private static final class Options {
        Path in;
        Path out;
        int[] crop;
        int scale = 1;
        double delayMs = 1000.0 / 30.0;
        double holdLastMs;
        int from;
        int to = -1;
        long maxBytes = 4_000_000L;
        int maxWidth = 800;

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (i + 1 >= args.length) {
                    usage("missing value for " + a);
                }
                String v = args[++i];
                switch (a) {
                    case "--in" -> o.in = Path.of(v);
                    case "--out" -> o.out = Path.of(v);
                    case "--crop" -> {
                        String[] p = v.split(",");
                        if (p.length != 4) {
                            usage("--crop needs X,Y,W,H");
                        }
                        o.crop = Arrays.stream(p).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
                    }
                    case "--scale" -> o.scale = Integer.parseInt(v);
                    case "--delay" -> o.delayMs = Double.parseDouble(v);
                    case "--hold-last" -> o.holdLastMs = Double.parseDouble(v);
                    case "--from" -> o.from = Integer.parseInt(v);
                    case "--to" -> o.to = Integer.parseInt(v);
                    case "--max-bytes" -> o.maxBytes = Long.parseLong(v);
                    case "--max-width" -> o.maxWidth = Integer.parseInt(v);
                    default -> usage("unknown option " + a);
                }
            }
            if (o.in == null || o.out == null) {
                usage("--in and --out are required");
            }
            if (o.scale < 1 || o.delayMs < 10 || o.holdLastMs < 0) {
                usage("--scale must be >= 1, --delay >= 10 ms, --hold-last >= 0");
            }
            return o;
        }

        static void usage(String problem) {
            System.err.println("MakeGif: " + problem);
            System.err.println("usage: java tools/MakeGif.java --in DIR --out FILE.gif [--crop X,Y,W,H] [--scale N]"
                    + " [--delay MS] [--from I] [--to I] [--hold-last MS] [--max-bytes N] [--max-width PX]");
            System.exit(2);
        }
    }

    // ================================================================== colours

    /** Open-addressing map RGB -> count. */
    static final class ColorCounts {
        int[] keys = new int[1 << 12];
        long[] counts = new long[1 << 12];
        boolean[] used = new boolean[1 << 12];
        int n;
        long total;

        void add(int rgb) {
            total++;
            int mask = keys.length - 1;
            int i = mix(rgb) & mask;
            while (used[i]) {
                if (keys[i] == rgb) {
                    counts[i]++;
                    return;
                }
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = rgb;
            counts[i] = 1;
            if (++n * 2 > keys.length) {
                grow();
            }
        }

        private void grow() {
            int[] k = keys;
            long[] c = counts;
            boolean[] u = used;
            keys = new int[k.length * 2];
            counts = new long[k.length * 2];
            used = new boolean[k.length * 2];
            int mask = keys.length - 1;
            for (int j = 0; j < k.length; j++) {
                if (u[j]) {
                    int i = mix(k[j]) & mask;
                    while (used[i]) {
                        i = (i + 1) & mask;
                    }
                    used[i] = true;
                    keys[i] = k[j];
                    counts[i] = c[j];
                }
            }
        }

        int size() {
            return n;
        }

        /** Colours and their counts, most frequent first. */
        int[] colorsByCount(long[] countsOut) {
            Integer[] order = new Integer[n];
            int[] cols = new int[n];
            long[] cnt = new long[n];
            int k = 0;
            for (int i = 0; i < keys.length; i++) {
                if (used[i]) {
                    cols[k] = keys[i];
                    cnt[k] = counts[i];
                    order[k] = k;
                    k++;
                }
            }
            Arrays.sort(order, (a, b) -> Long.compare(cnt[b], cnt[a]));
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                out[i] = cols[order[i]];
                countsOut[i] = cnt[order[i]];
            }
            return out;
        }

        static int mix(int x) {
            x *= 0x9E3779B1;
            return x ^ (x >>> 15);
        }
    }

    /** Perceptually weighted squared RGB distance (weights 2, 4, 3). */
    static int dist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db;
    }

    static final class Palette {
        /** Share of all pixels a colour needs to be kept exactly (and at most this many such colours). */
        private static final double PROTECT_SHARE = 0.0005;
        private static final int PROTECT_MAX = 128;
        private static final int KMEANS_ROUNDS = 6;

        static int[] build(ColorCounts counts) {
            int n = counts.size();
            long[] cnt = new long[n];
            int[] cols = counts.colorsByCount(cnt);
            if (n <= MAX_COLORS) {
                return cols.clone();
            }
            // 1. the most frequent colours exactly (flat UI and text colours)
            int protect = 0;
            while (protect < PROTECT_MAX && protect < n && cnt[protect] >= counts.total * PROTECT_SHARE) {
                protect++;
            }
            int[] palette = new int[MAX_COLORS];
            System.arraycopy(cols, 0, palette, 0, protect);
            // 2. median cut of everything else into the remaining entries
            int rest = n - protect;
            int[] rc = Arrays.copyOfRange(cols, protect, n);
            long[] rw = Arrays.copyOfRange(cnt, protect, n);
            int[] cut = medianCut(rc, rw, Math.min(MAX_COLORS - protect, rest));
            System.arraycopy(cut, 0, palette, protect, cut.length);
            int size = protect + cut.length;
            palette = Arrays.copyOf(palette, size);
            // 3. k-means refinement of the median-cut entries (the protected ones stay fixed)
            double[] sr = new double[size];
            double[] sg = new double[size];
            double[] sb = new double[size];
            double[] sw = new double[size];
            for (int round = 0; round < KMEANS_ROUNDS; round++) {
                Arrays.fill(sr, 0);
                Arrays.fill(sg, 0);
                Arrays.fill(sb, 0);
                Arrays.fill(sw, 0);
                for (int i = 0; i < n; i++) {
                    int c = cols[i];
                    int best = nearest(palette, c);
                    double wgt = cnt[i];
                    sr[best] += ((c >> 16) & 255) * wgt;
                    sg[best] += ((c >> 8) & 255) * wgt;
                    sb[best] += (c & 255) * wgt;
                    sw[best] += wgt;
                }
                for (int j = protect; j < size; j++) {
                    if (sw[j] > 0) {
                        int r = (int) Math.round(sr[j] / sw[j]);
                        int g = (int) Math.round(sg[j] / sw[j]);
                        int b = (int) Math.round(sb[j] / sw[j]);
                        palette[j] = (r << 16) | (g << 8) | b;
                    }
                }
            }
            return palette;
        }

        static int nearest(int[] palette, int c) {
            int best = 0;
            int bestD = Integer.MAX_VALUE;
            for (int j = 0; j < palette.length; j++) {
                int d = dist(palette[j], c);
                if (d < bestD) {
                    bestD = d;
                    best = j;
                    if (d == 0) {
                        break;
                    }
                }
            }
            return best;
        }

        /** Weighted median cut: splits the box with the largest weighted spread until there are {@code k} boxes. */
        static int[] medianCut(int[] cols, long[] w, int k) {
            List<int[]> boxes = new ArrayList<>(); // index ranges [from, to) into order
            Integer[] order = new Integer[cols.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            boxes.add(new int[]{0, cols.length});
            while (boxes.size() < k) {
                int bi = -1;
                double bestScore = -1;
                int bestAxis = 0;
                for (int b = 0; b < boxes.size(); b++) {
                    int[] box = boxes.get(b);
                    if (box[1] - box[0] < 2) {
                        continue;
                    }
                    int[] lo = {255, 255, 255};
                    int[] hi = {0, 0, 0};
                    long weight = 0;
                    for (int i = box[0]; i < box[1]; i++) {
                        int c = cols[order[i]];
                        for (int ch = 0; ch < 3; ch++) {
                            int v = (c >> (16 - 8 * ch)) & 255;
                            lo[ch] = Math.min(lo[ch], v);
                            hi[ch] = Math.max(hi[ch], v);
                        }
                        weight += w[order[i]];
                    }
                    int[] wch = {2, 4, 3};
                    for (int ch = 0; ch < 3; ch++) {
                        double score = (double) (hi[ch] - lo[ch]) * wch[ch] * Math.sqrt(weight);
                        if (score > bestScore) {
                            bestScore = score;
                            bi = b;
                            bestAxis = ch;
                        }
                    }
                }
                if (bi < 0 || bestScore <= 0) {
                    break;
                }
                int[] box = boxes.get(bi);
                int shift = 16 - 8 * bestAxis;
                Arrays.sort(order, box[0], box[1], Comparator.comparingInt(i -> (cols[i] >> shift) & 255));
                long total = 0;
                for (int i = box[0]; i < box[1]; i++) {
                    total += w[order[i]];
                }
                long acc = 0;
                int split = box[0] + 1;
                for (int i = box[0]; i < box[1] - 1; i++) {
                    acc += w[order[i]];
                    split = i + 1;
                    if (acc * 2 >= total) {
                        break;
                    }
                }
                boxes.set(bi, new int[]{box[0], split});
                boxes.add(new int[]{split, box[1]});
            }
            int[] out = new int[boxes.size()];
            for (int b = 0; b < boxes.size(); b++) {
                int[] box = boxes.get(b);
                double r = 0;
                double g = 0;
                double bl = 0;
                double tw = 0;
                for (int i = box[0]; i < box[1]; i++) {
                    int c = cols[order[i]];
                    double wt = w[order[i]];
                    r += ((c >> 16) & 255) * wt;
                    g += ((c >> 8) & 255) * wt;
                    bl += (c & 255) * wt;
                    tw += wt;
                }
                out[b] = ((int) Math.round(r / tw) << 16) | ((int) Math.round(g / tw) << 8) | (int) Math.round(bl / tw);
            }
            return out;
        }
    }

    /** RGB -> palette index for every colour of the animation (nearest entry, no dithering). */
    static final class IndexMap {
        private final ColorCounts map;
        private final int[] index;

        IndexMap(ColorCounts counts, int[] palette) {
            map = counts;
            index = new int[counts.keys.length];
            for (int i = 0; i < counts.keys.length; i++) {
                if (counts.used[i]) {
                    index[i] = Palette.nearest(palette, counts.keys[i]);
                }
            }
        }

        int get(int rgb) {
            int mask = map.keys.length - 1;
            int i = ColorCounts.mix(rgb) & mask;
            while (map.keys[i] != rgb || !map.used[i]) {
                i = (i + 1) & mask;
            }
            return index[i];
        }
    }

    // ================================================================== GIF encoding

    static final class GifWriter {
        private final OutputStream out;
        private final Lzw lzw = new Lzw();

        GifWriter(OutputStream out, int w, int h, int[] palette) throws IOException {
            this.out = out;
            out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
            short16(w);
            short16(h);
            out.write(0xF7); // global colour table, 8 bits colour resolution, 256 entries
            out.write(0);
            out.write(0);
            for (int i = 0; i < 256; i++) {
                int c = i < palette.length ? palette[i] : 0;
                out.write((c >> 16) & 255);
                out.write((c >> 8) & 255);
                out.write(c & 255);
            }
            // loop forever
            out.write(new byte[]{0x21, (byte) 0xFF, 0x0B});
            out.write("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
            out.write(new byte[]{0x03, 0x01, 0x00, 0x00, 0x00});
        }

        void frame(byte[] pixels, int[] rect, int delayCs) throws IOException {
            out.write(new byte[]{0x21, (byte) 0xF9, 0x04});
            out.write((1 << 2) | 1); // disposal: do not dispose; transparent index present
            short16(delayCs);
            out.write(TRANSPARENT);
            out.write(0);
            out.write(0x2C);
            short16(rect[0]);
            short16(rect[1]);
            short16(rect[2]);
            short16(rect[3]);
            out.write(0);
            lzw.encode(pixels, out);
        }

        void finish() throws IOException {
            out.write(0x3B);
        }

        private void short16(int v) throws IOException {
            out.write(v & 255);
            out.write((v >> 8) & 255);
        }
    }

    /** GIF LZW compressor (8-bit pixels), in the structure of the classic compress/GIF encoders. */
    static final class Lzw {
        private static final int MIN_CODE_SIZE = 8;
        private static final int MAX_BITS = 12;
        private static final int MAX_MAX_CODE = 1 << MAX_BITS;
        private final int[] next = new int[MAX_MAX_CODE * 256];
        private final int[] stamp = new int[MAX_MAX_CODE * 256];
        private int generation;
        private final int clearCode = 1 << MIN_CODE_SIZE;
        private final int eofCode = clearCode + 1;
        private int nBits;
        private int maxCode;
        private int freeEnt;
        private boolean clearFlag;
        private int cur;
        private int curBits;
        private final byte[] block = new byte[255];
        private int blockLen;
        private OutputStream out;

        void encode(byte[] pixels, OutputStream os) throws IOException {
            out = os;
            out.write(MIN_CODE_SIZE);
            cur = 0;
            curBits = 0;
            blockLen = 0;
            nBits = MIN_CODE_SIZE + 1;
            maxCode = (1 << nBits) - 1;
            freeEnt = clearCode + 2;
            clearFlag = false;
            generation++;
            output(clearCode);
            int ent = pixels[0] & 255;
            for (int i = 1; i < pixels.length; i++) {
                int c = pixels[i] & 255;
                int key = (ent << 8) | c;
                if (stamp[key] == generation) {
                    ent = next[key];
                    continue;
                }
                output(ent);
                if (freeEnt < MAX_MAX_CODE) {
                    next[key] = freeEnt++;
                    stamp[key] = generation;
                } else {
                    generation++;
                    freeEnt = clearCode + 2;
                    clearFlag = true;
                    output(clearCode);
                }
                ent = c;
            }
            output(ent);
            output(eofCode);
            out.write(0); // block terminator
        }

        private void output(int code) throws IOException {
            cur |= code << curBits;
            curBits += nBits;
            while (curBits >= 8) {
                put(cur & 255);
                cur >>>= 8;
                curBits -= 8;
            }
            if (freeEnt > maxCode || clearFlag) {
                if (clearFlag) {
                    nBits = MIN_CODE_SIZE + 1;
                    maxCode = (1 << nBits) - 1;
                    clearFlag = false;
                } else {
                    nBits++;
                    maxCode = nBits == MAX_BITS ? MAX_MAX_CODE : (1 << nBits) - 1;
                }
            }
            if (code == eofCode) {
                while (curBits > 0) {
                    put(cur & 255);
                    cur >>>= 8;
                    curBits -= 8;
                }
                flushBlock();
            }
        }

        private void put(int b) throws IOException {
            block[blockLen++] = (byte) b;
            if (blockLen == 255) {
                flushBlock();
            }
        }

        private void flushBlock() throws IOException {
            if (blockLen > 0) {
                out.write(blockLen);
                out.write(block, 0, blockLen);
                blockLen = 0;
            }
        }
    }
}
