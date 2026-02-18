import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../models/story_segment.dart';
import '../models/choice.dart';
import '../models/utterance.dart';
import '../services/api_service.dart';
import '../services/audio_player_service.dart';
import 'end_screen.dart';

// --- Helpers narration / dialogues ---
class _NarrPart {
  final bool isQuote;
  final String text;
  const _NarrPart(this.isQuote, this.text);
}

class StoryScreen extends StatefulWidget {
  final ApiService api;
  final StorySegment segment;

  const StoryScreen({
    super.key,
    required this.api,
    required this.segment,
  });

  @override
  State createState() => _StoryScreenState();
}

class _StoryScreenState extends State<StoryScreen> {
  final AudioPlayerService _audio = AudioPlayerService();

  // ⚡️ Vitesse de lecture (1.0 = normal). +15% => 1.15
  static const double _speechSpeed = 1.1;

  late StorySegment current;

  bool _loading = false;
  bool _audioBusy = false;
  int _runId = 0;

  // --- Couleurs des options ---
  static const List<Color> _choiceColors = [
    Colors.blue,
    Colors.green,
    Colors.orange,
    Colors.purple,
    Colors.teal,
    Colors.red,
  ];

  Color _choiceColor(int index) => _choiceColors[index % _choiceColors.length];

  String _choiceColorName(int index) {
    switch (index % _choiceColors.length) {
      case 0:
        return "bleue";
      case 1:
        return "verte";
      case 2:
        return "orange";
      case 3:
        return "violette";
      case 4:
        return "turquoise";
      default:
        return "rouge";
    }
  }

  @override
  void initState() {
    super.initState();
    current = widget.segment;

    // ▶️ Lecture automatique à chaque chargement de l'écran
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      playSegmentAudio(current);
    });
  }

  @override
  void dispose() {
    _runId++;
    _audio.stop();
    _audio.dispose();
    super.dispose();
  }

  // --- quote helpers ---
  List<_NarrPart> _splitNarrationWithQuotes(String narration) {
    final s = narration;
    final parts = <_NarrPart>[];

    // Matches: "..." or « ... »
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
        .map(
          (p) => _NarrPart(
        p.isQuote,
        p.text.replaceAll(RegExp(r'\s+'), ' ').trim(),
      ),
    )
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

  bool _isHeroSpeaker(String speaker) {
    final sp = speaker.trim();
    if (sp.isEmpty) return false;
    if (sp.toUpperCase() == 'HERO') return true;

    final player = (widget.api.playerName ?? '').trim();
    if (player.isNotEmpty && sp.toLowerCase() == player.toLowerCase()) return true;

    return false;
  }

  Future<void> _playMp3Bytes(List<int> bytes) async {
    final data = Uint8List.fromList(bytes);
    // ✅ speed appliqué côté player (just_audio)
    await _audio.playMp3Bytes(data, speed: _speechSpeed);
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
        // ⚠️ tu peux laisser speed ici (utile si un jour tu repasses sur OpenAI tts-1-hd),
        // mais avec ElevenLabs ça ne change rien côté serveur.
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

    // 1) match exact normalisé
    final idx = queue.indexWhere((u) => _normalize(u.text) == qn);
    if (idx >= 0) return queue.removeAt(idx);

    // 2) match "contient" (utile si l'IA ajoute/retire un mot)
    final idx2 = queue.indexWhere((u) {
      final un = _normalize(u.text);
      return un.contains(qn) || qn.contains(un);
    });
    if (idx2 >= 0) return queue.removeAt(idx2);

    // 3) fallback FIFO
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
          final u = _pickUtteranceForQuote(text, utterQueue);

          final speaker = _safeSpeaker(u?.speaker);
          final gender = _safeGender(u?.gender);

          // ✅ Force CHILD UNIQUEMENT si le speaker est le héros
          final String ageGroup =
          _isHeroSpeaker(speaker) ? 'CHILD' : _safeAgeGroup(u?.ageGroup);

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
            speaker: 'CHOICE_NARRATOR',
            ageGroup: 'ADULT',
            gender: 'NEUTRAL',
            text: text,
          );

          if (!mounted || runId != _runId) return;
          await _playMp3Bytes(bytes);
        }
      }

      // Lecture des options (voix narrateur adulte neutre)
      for (int i = 0; i < seg.choices.length; i++) {
        final c = seg.choices[i];
        if (!mounted || runId != _runId) return;

        final t = c.text.trim();
        if (t.isEmpty) continue;

        final prefix = "L'option ${_choiceColorName(i)}.";
        await _speakNarrator(sessionId: seg.sessionId, text: "$prefix $t");
        if (!mounted || runId != _runId) return;
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

  Future<void> _speakNarrator({
    required String sessionId,
    required String text,
  }) async {
    final bytes = await _ttsUtterance(
      sessionId: sessionId,
      speaker: 'NARRATOR',
      ageGroup: 'ADULT',
      gender: 'NEUTRAL',
      text: text,
    );

    if (!mounted) return;
    await _playMp3Bytes(bytes);
  }

  Future<void> _playChoiceAudio(Choice c, int index) async {
    if (_audioBusy) {
      await _stopAudio();
    }

    final int runId = ++_runId;
    setState(() => _audioBusy = true);

    try {
      final t = c.text.trim();
      if (t.isEmpty) return;

      final prefix = "L'option ${_choiceColorName(index)}.";
      await _speakNarrator(sessionId: current.sessionId, text: "$prefix $t");
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

  Future<void> _tapChoice(Choice c) async {
    if (_loading) return;

    setState(() => _loading = true);
    await _stopAudio();

    try {
      final next = await widget.api.choose(c.id);
      if (!mounted) return;

      if (next.ended) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => EndScreen(api: widget.api, segment: next),
          ),
        );
        return;
      }

      setState(() => current = next);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        playSegmentAudio(next);
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur: $e")),
      );
    } finally {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final seg = current;

    final int chapter = seg.displaySegmentIndex + 1;
    final int total = seg.plannedSegments;
    final chapterText = (total > 0) ? "Chapitre $chapter/$total" : "Chapitre $chapter";

    final disabled = seg.disabledChoiceIds.map((e) => e.toLowerCase()).toSet();

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
            padding: const EdgeInsets.only(bottom: 6),
            child: Text(
              "Vie ${seg.livesRemaining}/${seg.livesTotal} • $chapterText",
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              textAlign: TextAlign.center,
            ),
          ),
        ),
        actions: [
          IconButton(
            tooltip: "Lecture audio",
            onPressed: _audioBusy ? null : () => playSegmentAudio(seg),
            icon: _audioBusy
                ? const SizedBox(
              width: 22,
              height: 22,
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
              const SizedBox(height: 8),
              if (_loading) const LinearProgressIndicator(),
              const SizedBox(height: 6),
              Column(
                children: seg.choices.asMap().entries.map((entry) {
                  final i = entry.key;
                  final c = entry.value;

                  final isDisabled = disabled.contains(c.id.toLowerCase());
                  final color = _choiceColor(i);

                  return Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Opacity(
                      opacity: isDisabled ? 0.45 : 1.0,
                      child: InkWell(
                        onTap: isDisabled ? null : () => _tapChoice(c),
                        borderRadius: BorderRadius.circular(12),
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(
                            vertical: 8,
                            horizontal: 12,
                          ),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(width: 1, color: color),
                            color: color.withOpacity(0.08),
                          ),
                          child: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  c.text,
                                  style: TextStyle(
                                    fontSize: 13,
                                    height: 1.25,
                                    color: color,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                              IconButton(
                                tooltip: "Relire l'option",
                                onPressed: isDisabled ? null : () => _playChoiceAudio(c, i),
                                icon: Icon(Icons.volume_up, color: color),
                                iconSize: 18,
                                padding: EdgeInsets.zero,
                                visualDensity: VisualDensity.compact,
                                constraints: const BoxConstraints.tightFor(width: 34, height: 34),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
