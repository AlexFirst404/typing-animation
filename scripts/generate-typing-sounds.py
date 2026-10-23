#!/usr/bin/env python3
"""Generate Typing Animation's original keyboard sound library (MIT, AlexFirst).

No recordings, samples, soundfonts, or downloaded assets are used. Each sound is
synthesized from seeded noise bursts and damped resonances, with separate key-down,
key-up and stabilizer contacts. Run from any directory:

    python scripts/generate-typing-sounds.py
    python scripts/generate-typing-sounds.py --check

Dependencies: Python 3.10+, numpy, scipy, soundfile (libsndfile with Vorbis).
The checked-in assets were made with numpy 2.4.6, scipy 1.18.1 and soundfile 0.14.0 /
libsndfile 1.2.2. Same library versions produce identical bytes. Ogg's otherwise
random stream serial is replaced with a deterministic ID and its CRC recalculated.
--check regenerates in memory and checks existing assets without changing them.
WAV previews and measurements are written only to the ignored .research folder.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import struct

import numpy as np
import scipy
from scipy import signal
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
RATE = 44_100
COUNTS = {"key": 4, "space": 2, "backspace": 2, "enter": 1}
PRESETS = ("typewriter", "mechanical", "soft", "laptop")
LENGTHS = {
    "typewriter": {"key": .145, "space": .175, "backspace": .195, "enter": .315},
    "mechanical": {"key": .105, "space": .140, "backspace": .120, "enter": .160},
    "soft": {"key": .100, "space": .125, "backspace": .115, "enter": .135},
    "laptop": {"key": .065, "space": .085, "backspace": .080, "enter": .100},
}
# Short-window RMS is intentionally lower for the two quiet presets. Unlike LUFS,
# this measurement remains meaningful for isolated events shorter than 400 ms.
RMS_DB = {"typewriter": -25.0, "mechanical": -26.0, "soft": -29.0, "laptop": -28.0}
HIGH_CUT = {"typewriter": 7_000, "mechanical": 6_200, "soft": 3_400, "laptop": 6_400}


def seed_for(name: str) -> int:
    return int.from_bytes(hashlib.sha256(("TypingAnimation-original-v1:" + name).encode()).digest()[:8], "little")


class Synth:
    def __init__(self, duration: float, seed: int):
        self.audio = np.zeros(round(duration * RATE), dtype=np.float64)
        self.random = np.random.default_rng(seed)

    def add(self, audio: np.ndarray, at: float, gain: float = 1.0) -> None:
        start = round(at * RATE)
        count = min(len(audio), len(self.audio) - start)
        if count > 0:
            self.audio[start:start + count] += audio[:count] * gain

    def noise(self, at: float, decay: float, low: float, high: float, gain: float,
              attack: float = .00035, length: float | None = None) -> None:
        length = length or min(decay * 7, .18)
        t = np.arange(round(length * RATE)) / RATE
        noise = self.random.normal(size=len(t))
        sos = signal.butter(2, [low, high], btype="bandpass", fs=RATE, output="sos")
        noise = signal.sosfilt(sos, noise)
        noise /= max(float(np.sqrt(np.mean(noise * noise))), 1e-9)
        envelope = (1 - np.exp(-t / attack)) * np.exp(-t / decay)
        self.add(noise * envelope, at, gain)

    def modes(self, at: float, frequencies: tuple[float, ...], decays: tuple[float, ...],
              gains: tuple[float, ...], detune: float = 1.0, attack: float = .0003) -> None:
        t = np.arange(len(self.audio) - round(at * RATE)) / RATE
        out = np.zeros(len(t))
        for freq, decay, gain in zip(frequencies, decays, gains):
            # Small random phase differences model varied contact positions; the
            # common attack envelope prevents instantaneous discontinuities.
            phase = self.random.uniform(-.35, .35)
            out += gain * np.sin(2 * np.pi * freq * detune * t + phase) * np.exp(-t / decay)
        out *= 1 - np.exp(-t / attack)
        self.add(out, at)


def typewriter(s: Synth, action: str, variant: int) -> None:
    tune = 1 + (variant - 2.5) * .018
    if action == "enter":
        # Lever, a short carriage slide/ratchet, quiet bell, then the end stop.
        s.noise(.000, .004, 850, 6200, .56)
        s.modes(.002, (240, 680, 1640), (.025, .021, .015), (.40, .14, .08))
        s.noise(.026, .065, 650, 4200, .055, attack=.014, length=.22)
        for at, gain in ((.038, .13), (.060, .12), (.084, .10), (.111, .09), (.142, .07)):
            s.noise(at, .0017, 1100, 5200, gain)
        s.modes(.023, (1390, 2265, 3410), (.095, .065, .038), (.055, .022, .009), attack=.001)
        s.noise(.185, .006, 400, 5500, .42)
        s.modes(.185, (170, 410, 890), (.032, .024, .016), (.43, .15, .05))
        s.noise(.221, .003, 900, 4300, .07)
    elif action == "space":
        s.modes(.000, (190, 465, 1040, 2220), (.026, .025, .020, .014), (.49, .23, .10, .07), tune)
        s.noise(.001, .004, 450, 6200, .57)
        s.noise(.012, .002, 1600, 6300, .16)
        s.noise(.049, .004, 650, 5400, .26)
        s.modes(.051, (310, 810, 1680), (.022, .019, .014), (.22, .08, .04), tune)
        s.noise(.079, .0017, 1200, 5100, .075)
    elif action == "backspace":
        s.modes(.000, (225, 575, 1370), (.023, .025, .020), (.40, .19, .08), tune)
        s.noise(.001, .004, 900, 6800, .48)
        for at, gain in ((.023, .29), (.043, .18), (.068, .10)):
            s.noise(at, .0028, 1150, 5900, gain)
        s.modes(.044, (390, 1090), (.026, .021), (.12, .06), tune)
    else:
        s.noise(.000, .0028, 1100, 6900, .68)
        s.modes(.000, (270, 640, 1450, 2700), (.018, .026, .023, .016), (.37, .21, .12, .055), tune)
        s.noise(.008, .0015, 2100, 6200, .21)
        s.noise(.041 + variant * .002, .003, 900, 5700, .25)
        s.modes(.045, (430, 1210, 2380), (.016, .020, .014), (.16, .08, .035), tune)
        s.noise(.066, .0015, 1600, 6200, .055)


def mechanical(s: Synth, action: str, variant: int) -> None:
    tune = 1 + (variant - 2.5) * .024
    large = action in ("space", "enter")
    body = .82 if large else 1.0
    s.modes(.000, (210 * body, 480 * body, 1110, 2420),
            (.017, .013, .008, .004), (.51, .25, .18, .080), tune)
    s.noise(.000, .0021, 650, 6000, .65)
    # Tactile leaf closes shortly before the keycap bottoms out.
    s.noise(.005, .0014, 1700, 5800, .44)
    release = {"key": .030, "space": .045, "backspace": .036, "enter": .052}[action] + variant * .001
    s.noise(release, .0023, 1100, 5600, .35)
    s.modes(release, (360 * body, 790, 1800), (.014, .010, .006), (.24, .095, .035), tune)
    if large:
        s.noise(.011, .0023, 400, 3200, .12)
        s.modes(.012, (170, 345), (.022, .016), (.19, .08), tune)
        s.noise(release + .011, .0018, 900, 4100, .10)
    if action == "backspace":
        s.modes(.007, (310, 680), (.017, .011), (.09, .065), tune)


def soft(s: Synth, action: str, variant: int) -> None:
    tune = 1 + (variant - 2.5) * .022
    large = action in ("space", "enter")
    body = .83 if large else 1.0
    # Heavily damped case/foam response: low thock, little exposed switch click.
    s.modes(.000, (165 * body, 330 * body, 760), (.014, .010, .005), (.76, .22, .07), tune, attack=.0007)
    s.noise(.000, .003, 180, 2400, .20, attack=.0006)
    s.noise(.005, .0018, 400, 2700, .055)
    release = {"key": .034, "space": .044, "backspace": .039, "enter": .048}[action]
    s.modes(release, (230 * body, 510), (.011, .007), (.18, .055), tune, attack=.0006)
    s.noise(release, .0024, 300, 2200, .07)
    if large:
        s.modes(.009, (115, 250), (.018, .011), (.16, .06), tune, attack=.001)
        s.noise(release + .009, .002, 300, 1800, .045)


def laptop(s: Synth, action: str, variant: int) -> None:
    tune = 1 + (variant - 2.5) * .021
    large = action in ("space", "enter")
    # Scissor mechanism: close-spaced, light plastic contacts, very little bass.
    s.noise(.000, .0018, 1300, 6300, .38)
    s.modes(.000, (740, 1760, 3270), (.0065, .0045, .0028), (.16, .10, .036), tune)
    s.noise(.0055, .0011, 2100, 6000, .19)
    release = {"key": .016, "space": .024, "backspace": .020, "enter": .027}[action]
    s.noise(release, .0015, 1100, 5700, .19)
    s.modes(release, (910, 2210), (.005, .0035), (.06, .035), tune)
    if large:
        s.modes(.000, (350, 700), (.012, .007), (.15, .045), tune)
        s.noise(.010, .0015, 900, 4000, .09)
    elif action == "backspace":
        s.modes(.002, (540,), (.009,), (.09,), tune)


SYNTHS = {"typewriter": typewriter, "mechanical": mechanical, "soft": soft, "laptop": laptop}


def master(audio: np.ndarray, preset: str, action: str) -> np.ndarray:
    audio = signal.sosfilt(signal.butter(2, 65, btype="highpass", fs=RATE, output="sos"), audio)
    audio = signal.sosfilt(signal.butter(4, HIGH_CUT[preset], fs=RATE, output="sos"), audio)
    # A mild rounded saturation joins the material layers. Filtering again keeps
    # its added harmonics out of the harsh upper treble and ultrasonic band.
    audio = np.tanh(audio * 1.15) / 1.15
    audio = signal.sosfilt(signal.butter(4, HIGH_CUT[preset], fs=RATE, output="sos"), audio)
    envelope = np.ones(len(audio))
    ramp = round(.00025 * RATE)
    envelope[:ramp] = np.linspace(0, 1, ramp)
    fade = round(.008 * RATE)
    envelope[-fade:] = np.linspace(1, 0, fade) ** 2
    audio *= envelope
    # Common 60 ms onset window avoids boosting a quiet long tail or a bell.
    window = audio[:round(.060 * RATE)]
    rms = np.sqrt(np.mean(window * window))
    action_gain = {"key": 0, "space": .6, "backspace": -.3, "enter": .3}[action]
    desired = 10 ** ((RMS_DB[preset] + action_gain) / 20)
    audio *= desired / max(rms, 1e-10)
    peak = np.max(np.abs(audio))
    audio *= min(1.0, .36 / max(peak, 1e-10))
    return audio.astype(np.float32)


def ogg_crc(page: bytearray) -> int:
    crc = 0
    for value in page:
        crc ^= value << 24
        for _ in range(8):
            crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xFFFFFFFF
    return crc


def encode(audio: np.ndarray, name: str) -> bytes:
    buffer = io.BytesIO()
    sf.write(buffer, audio, RATE, format="OGG", subtype="VORBIS", compression_level=.25)
    output = bytearray(buffer.getvalue())
    position = 0
    serial = seed_for(name) & 0xFFFFFFFF
    while position < len(output):
        assert output[position:position + 4] == b"OggS"
        segments = output[position + 26]
        size = 27 + segments + sum(output[position + 27:position + 27 + segments])
        page = output[position:position + size]
        struct.pack_into("<I", page, 14, serial)
        page[22:26] = b"\0\0\0\0"
        struct.pack_into("<I", page, 22, ogg_crc(page))
        output[position:position + size] = page
        position += size
    return bytes(output)


def measure(audio: np.ndarray) -> dict:
    spectrum = np.abs(np.fft.rfft(audio)) ** 2
    frequencies = np.fft.rfftfreq(len(audio), 1 / RATE)
    peak = float(np.max(np.abs(audio)))
    rms = float(np.sqrt(np.mean(audio * audio)))
    onset = audio[:round(.060 * RATE)]
    onset_rms = float(np.sqrt(np.mean(onset * onset)))
    oversampled_peak = float(np.max(np.abs(signal.resample_poly(audio, 4, 1))))
    return {
        "durationMs": round(len(audio) / RATE * 1000, 2),
        "peakDbFS": round(20 * math.log10(max(peak, 1e-12)), 2),
        "rmsDbFS": round(20 * math.log10(max(rms, 1e-12)), 2),
        "onset60msRmsDbFS": round(20 * math.log10(max(onset_rms, 1e-12)), 2),
        "oversampledPeakDbFS": round(20 * math.log10(max(oversampled_peak, 1e-12)), 2),
        "tailPeakLast16Samples": float(np.max(np.abs(audio[-16:]))),
        "spectralCentroidHz": round(float(np.sum(spectrum * frequencies) / np.sum(spectrum))),
        "energyAbove10kHzPercent": round(float(100 * np.sum(spectrum[frequencies > 10000]) / np.sum(spectrum)), 6),
        "peakAmplitude": peak,
    }


def preview(samples: dict[str, np.ndarray]) -> np.ndarray:
    # Small phrase, space, correction, then return. Timing is humanized but fixed.
    events = [(.08, "key_1"), (.24, "key_3"), (.37, "key_2"), (.55, "key_4"),
              (.72, "space_1"), (.91, "key_3"), (1.10, "key_1"), (1.24, "key_4"),
              (1.42, "key_2"), (1.76, "backspace_1"), (1.91, "backspace_2"),
              (2.16, "key_2"), (2.35, "key_4"), (2.67, "enter_1")]
    output = np.zeros(round(3.2 * RATE), dtype=np.float32)
    for at, name in events:
        wave = samples[name]
        start = round(at * RATE)
        output[start:start + len(wave)] += wave
    assert np.max(np.abs(output)) < .8
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify deterministic assets without writing")
    parser.add_argument("--preview-dir", type=Path, default=ROOT / ".research/typing-sounds")
    args = parser.parse_args()
    assets = ROOT / "common-resources/assets/typinganimation"
    events, records, previews = {}, [], []
    for preset in PRESETS:
        decoded_samples = {}
        for action, count in COUNTS.items():
            names = []
            for variant in range(1, count + 1):
                name = f"typing/{preset}/{action}_{variant}"
                s = Synth(LENGTHS[preset][action], seed_for(name))
                SYNTHS[preset](s, action, variant)
                wave = master(s.audio, preset, action)
                encoded = encode(wave, name)
                decoded, sample_rate = sf.read(io.BytesIO(encoded), dtype="float32")
                info = sf.info(io.BytesIO(encoded))
                assert sample_rate == RATE and info.channels == 1 and info.subtype == "VORBIS"
                metrics = measure(decoded)
                assert metrics["peakAmplitude"] < .45, (name, metrics)
                assert metrics["oversampledPeakDbFS"] < -6, (name, metrics)
                assert metrics["tailPeakLast16Samples"] < .001, (name, metrics)
                assert metrics["energyAbove10kHzPercent"] < .05, (name, metrics)
                path = assets / "sounds" / (name + ".ogg")
                if args.check:
                    assert path.read_bytes() == encoded, f"non-reproducible asset: {path}"
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(encoded)
                names.append("typinganimation:" + name)
                decoded_samples[f"{action}_{variant}"] = decoded
                records.append({"name": name, "preset": preset.upper(), "action": action,
                                "sampleRate": RATE, "channels": 1, "codec": "Vorbis",
                                "bytes": len(encoded), "sha256": hashlib.sha256(encoded).hexdigest(), **metrics})
            events[f"typing.{preset}.{action}"] = {
                "subtitle": f"typinganimation.sound.{preset}.{action}", "sounds": names,
            }
        reel = preview(decoded_samples)
        previews.append(reel)
        if not args.check:
            args.preview_dir.mkdir(parents=True, exist_ok=True)
            sf.write(args.preview_dir / f"{preset}-preview.wav", reel, RATE, subtype="PCM_16")
    manifest = json.dumps(events, indent=2, ensure_ascii=False) + "\n"
    manifest_path = assets / "sounds.json"
    if args.check:
        assert manifest_path.read_text(encoding="utf-8") == manifest, "sounds.json differs from generator"
    else:
        manifest_path.write_text(manifest, encoding="utf-8")
        gap = np.zeros(round(.4 * RATE), dtype=np.float32)
        sf.write(args.preview_dir / "all-presets-preview.wav",
                 np.concatenate([piece for reel in previews for piece in (reel, gap)]), RATE, subtype="PCM_16")
        report = {"originalProceduralAudio": True, "license": "MIT", "seedVersion": 1,
                  "presetOrder": list(PRESETS), "sampleRate": RATE,
                  "dependencies": {"numpy": np.__version__, "scipy": scipy.__version__,
                                   "soundfile": sf.__version__, "libsndfile": sf.__libsndfile_version__},
                  "files": records}
        (args.preview_dir / "metrics.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    summary = {}
    for preset in PRESETS:
        rows = [r for r in records if r['preset'] == preset.upper()]
        summary[preset] = {
            "files": len(rows), "durationMs": [min(r['durationMs'] for r in rows), max(r['durationMs'] for r in rows)],
            "peakDbFS": [min(r['peakDbFS'] for r in rows), max(r['peakDbFS'] for r in rows)],
            "onset60msRmsDbFS": [min(r['onset60msRmsDbFS'] for r in rows), max(r['onset60msRmsDbFS'] for r in rows)],
            "bytes": sum(r['bytes'] for r in rows),
        }
    print(json.dumps({"mode": "verified deterministic bytes" if args.check else "generated",
                      "events": len(events), "files": len(records), "presets": summary}, indent=2))


if __name__ == "__main__":
    main()
