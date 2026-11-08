# Custom typing sound packs

Open **Sound → Sound library** in Typing Animation settings. Import a ZIP by entering its local
file path, wait for Minecraft's resource reload to finish, preview the pack, then choose **Use**.
Selecting a previously imported pack does not need another resource reload. Choose **Built-in**
to return to the four original sound families. Sound playback still requires the normal typing
sound switch and respects its volume, pitch, deletion and Enter options.

Imported libraries are stored in `config/typinganimation-sounds/`. Missing or invalid packs fall
back to the selected built-in family. They work on the client only; servers receive no audio.
Ordinary Minecraft resource packs can still replace the built-in audio and take priority over
the local library's generated resources.

## ZIP format

Put `pack.json` at the root of the ZIP:

```json
{
  "format": 1,
  "name": "My keyboard",
  "sounds": {
    "key": ["samples/key_1.ogg", "samples/key_2.ogg"],
    "space": ["samples/space.ogg"],
    "backspace": ["samples/backspace.ogg"],
    "enter": ["samples/enter.ogg"]
  }
}
```

Each action accepts one path or an array of up to 16 paths; Minecraft randomly chooses a variant.
`key` is required. If `space`, `backspace` or `enter` is omitted, it uses the key samples.
Use **Ogg Vorbis**, mono or stereo, 8–192 kHz, at most ten seconds per sample. In practice,
short mono clips of roughly 20–200 ms work best for typing. MP3, WAV and Ogg Opus are not supported.

Paths are relative to the ZIP and may use simple folders, ASCII letters, digits, spaces,
hyphens, underscores and dots. Absolute paths, `..`, backslashes and duplicate entries are rejected.
Limits: 32 MiB per ZIP and expanded pack, 8 MiB per sample, 16 imported packs and 64 MiB of audio
in the local library. Only the manifest's referenced Ogg samples are installed; unrelated ZIP
contents are never added to Minecraft's resource packs.

To share a sound pack, share the original ZIP. User presets store its content ID, so importing
the same ZIP on another installation reconnects a preset to that pack. Audio is not embedded
inside a user preset file.
