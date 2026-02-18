import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../models/story_segment.dart';
import '../models/utterance.dart';
import '../services/api_service.dart';
import '../services/audio_player_service.dart';
import 'home_screen.dart';
import 'story_screen.dart';

class _NarrPart {
  final bool isQuote;
  final String text;
  const _NarrPart(this.isQuote, this.text);
}

class EndScreen extends StatefulWidget {
  final ApiService api;
  final StorySegment segment;

  const EndScreen({
    super.key,
    required this.api,
    required this.segment,
  });

  @override
  State<EndScreen> createState() => _EndScreenState();
}

class _EndScreenState extends State<EndScreen> {
  final AudioPlayerService _audio = AudioPlayerService();

  static const double _speechSpeed = 1.15;

  bool _audioBusy = false;
  int _runId = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      playSegmentAudio(widget.segment);
    });
  }

  @override
  void dispose() {
    _runId++;
    _audio.stop();
    _audio.dispose();
    super.dispose();
  }

  List<_NarrPart> _splitNarrationWithQuotes(String narration) {
    final s = narration;
    final parts = <_NarrPart>[];

    final reQuotes = RegExp(r'("([^"]+)")|(«([^»]+)»)', multiLine: true);
    int idx = 0;

    for (final m in reQuotes.allMatches(s)) {
      if (m.start > idx) {
        parts.add(_NarrPart(false, s.substring(idx, m.start)));
      }
      final quoted = (m.group(2) ?? m.group(4) ?? '').trim();
      parts.add(_NarrPart(true, quoted));
      idx = m.end;
    }
    if (idx < s.length) {
      parts.add(_NarrPart(false, s.substring(idx)));
    }

    return parts
        .map((p) => _NarrPart(
      p.isQuote,
      p.text.replaceAll(RegExp(r'\s+'), ' ').trim(),
    ))
        .where((p) => p.text.isNotEmpty)
        .toList();
  }

  String _normalize(String s) {
    return s
        .toLowerCase()
        .replaceAll(RegExp(r'[\s«»"“”]+'), ' ')
        .replaceAll(RegExp(r'[^\p{L}\p{N}\s]+', unicode: true), '')
        .trim();
  }

  String _safeSpeaker(String? s) {
    final v = (s ?? '').trim();
    return v.isEmpty ? 'HERO' : v;
  }

  String _safeAgeGroup(String? s) {
    final v = (s ?? '').trim().toUpperCase();
    if (v == 'ADULT' || v == 'CHILD') return v;
    return 'CHILD';
  }

  String _safeGender(String? s) {
    final v = (s ?? '').trim().toUpperCase();
    if (v == 'MALE' || v == 'FEMALE') return v;
    return 'NEUTRAL';
  }

  Future<void> _playMp3Bytes(List<int> bytes) async {
    final data = Uint8List.fromList(bytes);
    await _audio.playMp3Bytes(data);
  }

  Future<List<int>> _ttsUtterance({
    required String sessionId,
    required String speaker,
    required String ageGroup,
    required String gender,
    required String text,
  }) async {
    final base = widget.api.baseUrl.endsWith('/')
        ? widget.api.baseUrl.substring(0, widget.api.baseUrl.length - 1)
        : widget.api.baseUrl;

    final uri = Uri.parse("$base/api/tts/$sessionId/utterance");
    final res = await http.post(
      uri,
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({
        "speaker": speaker,
        "ageGroup": ageGroup,
        "gender": gender,
        "text": text,
        "speed": _speechSpeed,
      }),
    );

    if (res.statusCode >= 400) {
      throw Exception("TTS failed ${res.statusCode}: ${res.body}");
    }
    return res.bodyBytes;
  }

  Utterance? _pickUtteranceForQuote(String quote, List<Utterance> queue) {
    if (queue.isEmpty) return null;

    final qn = _normalize(quote);

    final idx = queue.indexWhere((u) => _normalize(u.text) == qn);
    if (idx >= 0) return queue.removeAt(idx);

    final idx2 = queue.indexWhere((u) {
      final un = _normalize(u.text);
      return un.contains(qn) || qn.contains(un);
    });
    if (idx2 >= 0) return queue.removeAt(idx2);

    return queue.removeAt(0);
  }

  Future<void> playSegmentAudio(StorySegment seg) async {
    if (_audioBusy) return;

    final int runId = ++_runId;
    setState(() => _audioBusy = true);

    try {
      await _audio.stop();

      final parts = _splitNarrationWithQuotes(seg.narration);
      final utterQueue = List<Utterance>.from(seg.utterances);

      for (final part in parts) {
        if (!mounted || runId != _runId) return;

        final text = part.text.trim();
        if (text.isEmpty) continue;

        if (part.isQuote) {
          final u        = _pickUtteranceForQuote(text, utterQueue);
          final speaker  = _safeSpeaker(u?.speaker);
          final ageGroup = _safeAgeGroup(u?.ageGroup);
          final gender   = _safeGender(u?.gender);

          final bytes = await _ttsUtterance(
            sessionId: seg.sessionId,
            speaker: speaker,
            ageGroup: ageGroup,
            gender: gender,
            text: text,
          );

          if (!mounted || runId != _runId) return;
          await _playMp3Bytes(bytes);
        } else {
          final bytes = await _ttsUtterance(
            sessionId: seg.sessionId,
            speaker: 'NARRATOR',
            ageGroup: 'ADULT',
            gender: 'NEUTRAL',
            text: text,
          );

          if (!mounted || runId != _runId) return;
          await _playMp3Bytes(bytes);
        }
      }

      final expl = seg.explanation.trim();
      if (expl.isNotEmpty) {
        final bytes = await _ttsUtterance(
          sessionId: seg.sessionId,
          speaker: 'NARRATOR',
          ageGroup: 'ADULT',
          gender: 'NEUTRAL',
          text: "Explication : $expl",
        );

        if (!mounted || runId != _runId) return;
        await _playMp3Bytes(bytes);
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur audio: $e")),
      );
    } finally {
      if (!mounted) return;
      if (runId == _runId) setState(() => _audioBusy = false);
    }
  }

  Future<void> _stopAudio() async {
    _runId++;
    await _audio.stop();
    if (mounted) setState(() => _audioBusy = false);
  }

  @override
  Widget build(BuildContext context) {
    final seg = widget.segment;

    final int chapter = seg.displaySegmentIndex + 1;
    final int total = seg.plannedSegments;
    final chapterText =
    (total > 0) ? "Chapitre $chapter/$total" : "Chapitre $chapter";

    final outOfLives = seg.livesRemaining <= 0;

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 92,
        centerTitle: true,
        title: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Text(
            seg.title,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
          ),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(36),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Text(
              "Vies ${seg.livesRemaining}/${seg.livesTotal} • $chapterText",
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              textAlign: TextAlign.center,
            ),
          ),
        ),
        actions: [
          IconButton(
            tooltip: _audioBusy ? "Lecture..." : "Lecture audio",
            onPressed: _audioBusy ? null : () => playSegmentAudio(seg),
            icon: _audioBusy
                ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
                : const Icon(Icons.play_arrow),
          ),
          IconButton(
            tooltip: "Stop",
            onPressed: _audioBusy ? _stopAudio : null,
            icon: const Icon(Icons.stop),
          ),
        ],

      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
          child: Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  child: Text(
                    seg.narration,
                    style: const TextStyle(fontSize: 16, height: 1.5),
                  ),
                ),
              ),
              const Divider(height: 26),
              Text(
                seg.explanation,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  fontStyle: FontStyle.italic,
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () async {
                    await _stopAudio();
                    if (!context.mounted) return;

                    if (outOfLives) {
                      Navigator.pushAndRemoveUntil(
                        context,
                        MaterialPageRoute(builder: (_) => HomeScreen()),
                            (_) => false,
                      );
                      return;
                    }

                    final back = await widget.api.rewind();
                    if (!context.mounted) return;

                    Navigator.pushReplacement(
                      context,
                      MaterialPageRoute(
                        builder: (_) => StoryScreen(api: widget.api, segment: back),
                      ),
                    );
                  },
                  child: Text(outOfLives ? "🏁 Fin" : "🔁 Revenir en arrière"),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
