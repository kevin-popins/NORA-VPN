# Android background assets

PNG masters live in `assets/backgrounds/masters/`, outside the Android source
sets. The APK includes only the generated WebP files under
`app/src/main/res/drawable-nodpi/`. All existing scenes are retained; Antarctica
adds five resources named `nora_location_antarctica1` through
`nora_location_antarctica5`.

## Generate and verify

The encoder is pinned to Pillow 11.3.0 with libwebp 1.5.0. The script checks both
versions before encoding so that generated resources can be reproduced exactly.

```sh
python -m pip install -r tools/requirements-background-assets.txt
python tools/background_assets.py build
python tools/background_assets.py verify --reencode
```

`assets/backgrounds/manifest.json` records source and output paths, hashes,
dimensions, byte counts and encoder settings for all 52 resources. Verification
checks the complete inventory, hashes, dimensions and pixel equality for RGBA
images. `--reencode` also checks that regeneration produces identical bytes.

Opaque scenes use WebP quality 95, method 6. Transparent artwork uses lossless
WebP with exact RGBA preservation, including RGB values behind transparent pixels.
The conversion preserves native dimensions, aspect ratios and drawable names;
it does not resize the images. Images remain available offline. Numeric Android
resource IDs may change between builds and must not be stored as profile IDs.

## Compare image quality

```sh
python tools/background_assets.py gallery
python tools/background_assets.py compare --names antarctica1 antarctica2 antarctica3 antarctica4 antarctica5 universe germany3 poland3 --display-size 990 534
```

Comparison images are written to the ignored `build/background-qa/` directory.
The script compares q88, q92 and q95 using native-resolution crops and previews
at the requested physical display dimensions. Inspect sky gradients, snow,
fine details, dark scenes and transparent edges visually; numeric metrics are
additional diagnostics.

The complete PNG master set is 118,274,151 bytes; generated resources total
22,919,980 bytes. These image-file sizes are separate from APK size, installed
size and decoded bitmap memory. A 1672×941 ARGB frame still needs approximately
6 MiB after decoding.

## Restore PNG resources

To undo image compression, copy each manifest-listed master back into
`drawable-nodpi/` and remove its corresponding WebP. Preserve every resource
stem and never leave both formats with the same stem in the resource directory.
This does not require changes to profiles, subscriptions or transport data.
