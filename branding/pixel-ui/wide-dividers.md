# Wide pixel dividers

`divider-01-wide.svg` through `divider-04-wide.svg` reproduce the four original 96×12 pixel grids exactly. Their intrinsic size is 2304×288 (24×), with `viewBox="0 0 96 12"`, `shape-rendering="crispEdges"` and `preserveAspectRatio="xMidYMid meet"`.

Each SVG contains only horizontal rectangles spanning adjacent identical opaque pixels. The original RGB palette, geometry and alpha mask are preserved. The existing approved cleanup is retained: only the 796/700 pure-black background pixels in dividers 02/03 become transparent. No new colors, interpolation, embedded bitmap, script, font or external resource is used.

The larger intrinsic width lets a host that permits SVG images constrain the image to its content-column width. Aspect ratio remains 8:1, and the crisp-edge rendering hint avoids smooth color interpolation. Actual HTML/CSS constraints remain the host platform's responsibility.

Run `python scripts/prepare-pixel-ui.py --wide-svg-only` to regenerate only these four SVGs and their separate verification manifest. The normal pipeline also generates them, without adding them to or changing the existing PNG archive. `wide-dividers-manifest.json` records source/output hashes and independently expands the written SVG rectangles into a 96×12 RGBA matrix, proving equality with the cleaned source and the existing 3× PNG.
