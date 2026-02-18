import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';

class AudioPlayerService {
  final AudioPlayer _player = AudioPlayer();

  bool _stopped = false;
  Completer<void>? _completer;
  StreamSubscription<ProcessingState>? _sub;

  double _speed = 1.0;

  /// Set playback speed for next plays (and immediately if currently playing).
  Future<void> setSpeed(double speed) async {
    if (speed.isNaN || speed.isInfinite || speed <= 0) return;
    _speed = speed;

    // Apply immediately if possible (won't throw in most cases; guard just in case)
    try {
      await _player.setSpeed(_speed);
    } catch (_) {
      // ignore: some platforms/codecs may not support rate changes in every state
    }
  }

  Future<void> playMp3Bytes(Uint8List bytes, {double? speed}) async {
    _stopped = false;
    if (bytes.isEmpty) return;

    if (speed != null) {
      await setSpeed(speed);
    }

    final dir = await getTemporaryDirectory();
    final file = File("${dir.path}/tts_${DateTime.now().millisecondsSinceEpoch}.mp3");
    await file.writeAsBytes(bytes, flush: true);

    if (_stopped) return;

    await _player.stop();
    if (_stopped) return;

    await _player.setFilePath(file.path);
    if (_stopped) return;

    // Apply speed AFTER setting the source (more reliable across platforms)
    try {
      await _player.setSpeed(_speed);
    } catch (_) {
      // ignore
    }

    // Completer résolu soit par la fin naturelle, soit par stop() externe
    final c = Completer<void>();
    _completer = c;

    await _sub?.cancel();
    _sub = _player.processingStateStream.listen((s) {
      if (s == ProcessingState.completed && !c.isCompleted) {
        c.complete();
      }
    });

    await _player.play();
    await c.future;

    await _sub?.cancel();
    _sub = null;
    _completer = null;
  }

  Future<void> stop() async {
    _stopped = true;

    await _sub?.cancel();
    _sub = null;

    final c = _completer;
    _completer = null;
    if (c != null && !c.isCompleted) c.complete();

    await _player.stop();
  }

  void dispose() {
    _stopped = true;

    _sub?.cancel();
    _sub = null;

    final c = _completer;
    _completer = null;
    if (c != null && !c.isCompleted) c.complete();

    _player.dispose();
  }
}
