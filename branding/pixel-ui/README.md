# Pixel UI artwork

Twelve user-supplied pixel PNGs, preserved as original files and exported as 25 crisp, usable PNG variants. Every enlargement uses an integer scale and nearest-neighbor sampling. No artwork was redrawn and no smoothing, blur, or new colors were added.

## Files and mapping

| Attachment | Byte-for-byte original | Usable variants | Transparency handling |
| --- | --- | --- | --- |
| 1 | `originals/divider-01.png` | `divider-01-3x.png` (288×36), `divider-01-6x.png` (576×72), `divider-01-8x.png` (768×96) | Unchanged RGBA pixels |
| 2 | `originals/divider-02.png` | `divider-02-3x.png` (288×36), `divider-02-6x.png` (576×72), `divider-02-8x.png` (768×96) | Only 796 pure-black background pixels made transparent |
| 3 | `originals/divider-03.png` | `divider-03-3x.png` (288×36), `divider-03-6x.png` (576×72), `divider-03-8x.png` (768×96) | Only 700 pure-black background pixels made transparent |
| 4 | `originals/divider-04.png` | `divider-04-3x.png` (288×36), `divider-04-6x.png` (576×72), `divider-04-8x.png` (768×96) | Unchanged RGBA pixels |
| 5 | `originals/platform-modrinth.png` | `platform-modrinth-4x.png` (64×64) | Unchanged RGBA pixels |
| 6 | `originals/platform-curseforge.png` | `platform-curseforge-4x.png` (64×64) | Unchanged RGBA pixels |
| 7 | `originals/platform-github.png` | `platform-github-4x.png` (64×64) | Unchanged RGBA pixels |
| 8 | `originals/mini-icon-01.png` | `mini-icon-01-2x.png` (24×24), `mini-icon-01-4x.png` (48×48) | Unchanged RGBA pixels |
| 9 | `originals/mini-icon-02.png` | `mini-icon-02-2x.png` (24×24), `mini-icon-02-4x.png` (48×48) | Unchanged RGBA pixels |
| 10 | `originals/mini-icon-03.png` | `mini-icon-03-2x.png` (24×24), `mini-icon-03-4x.png` (48×48) | Unchanged RGBA pixels |
| 11 | `originals/mini-icon-04.png` | `mini-icon-04-2x.png` (24×24), `mini-icon-04-4x.png` (48×48) | Unchanged RGBA pixels |
| 12 | `originals/point.png` | `point-2x.png` (32×32), `point-inline-2x.png` (16×16) | Unchanged RGBA pixels |

The mini icons from attachments 8–11 intentionally have opaque purple corners. Those pixels remain opaque in every output. The inline point is the original point's exact alpha bounding box `(4, 4, 12, 12)`, cropped to 8×8 before a 2× enlargement to 16×16. The uncropped point is also provided at 32×32.

Only attachments 2 and 3 need background cleanup for web use: their original RGB files have 796 and 700 pure-black `(0, 0, 0)` background pixels respectively. Those pixels receive alpha 0 in the derived PNGs. Every RGB triplet is retained, every nonblack pixel is unchanged, and both originals remain byte-for-byte intact.

Use the 288×36 divider variants in compact/mobile descriptions; 576×72 and 768×96 variants are also available. Render a PNG at its natural dimensions, or another integer scale, to keep its pixel grid sharp.

## Reproduce and verify

Run `python scripts/prepare-pixel-ui.py` from the repository. It rebuilds from the preserved originals, verifies every enlarged pixel block, RGBA palette and alpha histogram, writes `manifest.json`, and creates `dist/typinganimation-pixel-ui.zip`. Initial import uses `--source-map path/to/map.json`, where the JSON object maps attachment numbers `"1"` through `"12"` to the source PNG paths. Import refuses to replace an original with different bytes.

The manifest records the attachment mapping, source SHA-256 hashes, scale factors, dimensions, alpha cleanup and output hashes. PNGs omit inherited metadata and use fixed compression settings. ZIP entries use sorted names, fixed timestamps and fixed permissions, so repeated builds with the same Pillow runtime are byte-identical. The ZIP contains all 25 usable PNGs, the 12 originals, this README and the manifest.

The script also writes `.research/pixel-ui-contact-sheet.png` for visual review on light and dark backgrounds. This review sheet is excluded from the distributable archive.
