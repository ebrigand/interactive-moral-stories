import 'package:flutter_tts/flutter_tts.dart';

enum TtsState { stopped, playing, paused }

class TtsService {
  final FlutterTts _tts = FlutterTts();
  TtsState state = TtsState.stopped;

  Future<void> init({
    String language = "fr-FR",
    double rate = 0.47,
    double pitch = 1.0,
    double volume = 1.0,
  }) async {
    await _tts.setLanguage(language);
    await _tts.awaitSpeakCompletion(true);
    await _tts.setSpeechRate(rate);
    await _tts.setPitch(pitch);
    await _tts.setVolume(volume);
    await _tts.setVoice({
      "name": "fr-FR-Wavenet-B",
      "locale": "fr-FR",
    });

    // callbacks (selon plateforme)
    _tts.setStartHandler(() => state = TtsState.playing);
    _tts.setCompletionHandler(() => state = TtsState.stopped);
    _tts.setCancelHandler(() => state = TtsState.stopped);
    _tts.setPauseHandler(() => state = TtsState.paused);
    _tts.setContinueHandler(() => state = TtsState.playing);
    _tts.setErrorHandler((_) => state = TtsState.stopped);

    // iOS: jouer même si silencieux
    await _tts.setIosAudioCategory(
      IosTextToSpeechAudioCategory.playback,
      [
        IosTextToSpeechAudioCategoryOptions.mixWithOthers,
      ],
      IosTextToSpeechAudioMode.defaultMode,
    );
  }

  Future<void> speak(String text) async {
    if (text.trim().isEmpty) return;
    await _tts.stop();
    await _tts.speak(text);
  }

  Future<void> pause() async {
    await _tts.pause(); // Android support partiel, iOS OK
    state = TtsState.paused;
  }

  Future<void> stop() async {
    await _tts.stop();
    state = TtsState.stopped;
  }

  Future<void> dispose() async {
    await _tts.stop();
  }
}
