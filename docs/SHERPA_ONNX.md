# Upgrading the wake word to sherpa-onnx (optional, untested)

The default engine is Android's speech recogniser. sherpa-onnx is a dedicated on-device keyword spotter: lower battery, no internet, no Google speech service.

This was NOT integrated or tested here because the workspace could not download the sherpa-onnx library or its models.

Outline:
1. Download from https://github.com/k2-fsa/sherpa-onnx/releases : the Android AAR (`sherpa-onnx-<version>.aar`) and a keyword-spotting model such as `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-*`.
2. Put the AAR in `app/libs/` and add `implementation(files("libs/sherpa-onnx-<version>.aar"))`. Put the model files in `app/src/main/assets/kws/`.
3. Make a `keywords.txt` with `sherpa-onnx-cli text2token` for "ARISE" and "HEY ARISE" (see the sherpa-onnx keyword-spotting docs for the exact token format).
4. In `AssistantService`, replace the `Purpose.WAKE` branch of `doListen()` with an `AudioRecord` loop (16 kHz mono, 100 ms chunks) feeding `KeywordSpotter`; on a detection call `onWake("")`. Keep `SpeechRecognizer` for `COMMAND`, `FOLLOWUP` and `CONFIRM`.
Check the current sherpa-onnx Kotlin API names against their docs before coding; they change between versions.
