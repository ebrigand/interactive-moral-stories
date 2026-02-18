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

  Future<void> playMp3Bytes(Uint8List bytes) async {
    _stopped = false;
    if (bytes.isEmpty) return;

    final dir = await getTemporaryDirectory();
    final file = File("${dir.path}/tts_${DateTime.now().millisecondsSinceEpoch}.mp3");
    await file.writeAsBytes(bytes, flush: true);

    if (_stopped) return;
    await _player.stop();
    if (_stopped) return;
    await _player.setFilePath(file.path);
    if (_stopped) return;

    // Completer résolu soit par la fin naturelle, soit par stop() externe
    final c = Completer<void>();
    _completer = c;

    await _sub?.cancel();
    _sub = _player.processingStateStream.listen((s) {
      if (s == ProcessingState.completed && !c.isCompleted) c.complete();
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
