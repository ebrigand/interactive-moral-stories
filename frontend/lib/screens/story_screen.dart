import 'dart:typed_data';
import 'package:flutter/material.dart';

import '../models/story_segment.dart';
import '../models/choice.dart';
import '../services/api_service.dart';
import '../services/audio_player_service.dart';
import 'end_screen.dart';

class StoryScreen extends StatefulWidget {
  final ApiService api;
  final StorySegment segment;

  const StoryScreen({
    super.key,
    required this.api,
    required this.segment,
  });

  @override
  State<StoryScreen> createState() => _StoryScreenState();
}

class _StoryScreenState extends State<StoryScreen> {
  late StorySegment current;
  late List<Choice> displayedChoices;

  final AudioPlayerService _audio = AudioPlayerService();

  bool _loading = false;
  bool _audioLoading = false;

  static const List<_ChoiceColor> _palette = [
    _ChoiceColor("rouge", Colors.red),
    _ChoiceColor("bleue", Colors.blue),
    _ChoiceColor("verte", Colors.green),
    _ChoiceColor("jaune", Colors.amber),
  ];

  @override
  void initState() {
    super.initState();
    _setSegment(widget.segment);
  }

  @override
  void dispose() {
    _audio.dispose();
    super.dispose();
  }

  void _setSegment(StorySegment seg) {
    current = seg;
    displayedChoices = List<Choice>.from(seg.choices)..shuffle();
    _audio.stop();
  }

  _ChoiceColor _colorForIndex(int i) => _palette[i % _palette.length];

  bool _isChoiceDisabled(String id) {
    final list = current.disabledChoiceIds;
    if (list.isEmpty) return false;
    final needle = id.trim().toLowerCase();
    return list.any((x) => x.trim().toLowerCase() == needle);
  }

  Uint8List _asU8(List<int> bytes) => Uint8List.fromList(bytes);

  // ✅ EXACTEMENT ton flow, avec conversion Uint8List
  Future<void> playSegmentAudio(StorySegment seg) async {
    if (_audioLoading) return;

    setState(() => _audioLoading = true);
    try {
      // 1️⃣ Narration (toujours adulte)
      final narrationBytes = await widget.api.ttsUtterance(
        sessionId: seg.sessionId,
        speaker: 'NARRATOR',
        ageGroup: 'ADULT',
        text: seg.narration,
      );
      await _audio.playMp3Bytes(_asU8(narrationBytes));

      // 2️⃣ Dialogues (enfants / adultes selon utterances)
      for (final u in seg.utterances) {
        if (u.text.trim().isEmpty) continue;

        final bytes = await widget.api.ttsUtterance(
          sessionId: seg.sessionId,
          speaker: u.speaker,
          ageGroup: u.ageGroup,
          text: u.text,
        );
        await _audio.playMp3Bytes(_asU8(bytes));
      }

      // 3️⃣ Lire les options (avec couleur)
      for (int i = 0; i < displayedChoices.length; i++) {
        final meta = _colorForIndex(i);
        final opt = displayedChoices[i];

        final bytes = await widget.api.ttsUtterance(
          sessionId: seg.sessionId,
          speaker: 'NARRATOR',
          ageGroup: 'ADULT',
          text: "Option ${meta.spokenName} : ${opt.text}",
        );
        await _audio.playMp3Bytes(_asU8(bytes));
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur audio: $e")),
      );
    } finally {
      if (mounted) setState(() => _audioLoading = false);
    }
  }

  Future<void> _playOptionAudio(int index, Choice choice) async {
    if (_audioLoading) return;

    setState(() => _audioLoading = true);
    try {
      final meta = _colorForIndex(index);

      final bytes = await widget.api.ttsUtterance(
        sessionId: current.sessionId,
        speaker: 'NARRATOR',
        ageGroup: 'ADULT',
        text: "Option ${meta.spokenName} : ${choice.text}",
      );
      await _audio.playMp3Bytes(_asU8(bytes));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur audio option: $e")),
      );
    } finally {
      if (mounted) setState(() => _audioLoading = false);
    }
  }

  Future<void> _stopAudio() async {
    await _audio.stop();
    if (mounted) setState(() {});
  }

  Future<void> _choose(String choiceId) async {
    if (_loading) return;
    if (_isChoiceDisabled(choiceId)) return;

    setState(() => _loading = true);

    try {
      // 👉 adapte ici si ta méthode s'appelle choose(...)
      final next = await widget.api.choose(choiceId);

      if (!mounted) return;

      if (next.ended) {
        await _audio.stop();
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => EndScreen(
              api: widget.api,
              segment: next,
            ),
          ),
        );
        return;
      }

      setState(() {
        _setSegment(next);
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur : $e")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final int chap = current.segmentIndex + 1;
    final int total = current.plannedSegments;
    final chapterText = (total > 0) ? "Chapitre $chap/$total" : "Chapitre $chap";

    final titleLine = Text(
      current.title,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
    );

    final statsLine = Text(
      "Vies ${current.livesRemaining}/${current.livesTotal} • $chapterText",
      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
      textAlign: TextAlign.center,
    );

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 76,
        centerTitle: true,
        title: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: titleLine,
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(32),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: statsLine,
          ),
        ),
        actions: [
          IconButton(
            tooltip: "Lecture audio",
            onPressed: _audioLoading ? null : () => playSegmentAudio(current),
            icon: _audioLoading
                ? const SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
                : const Icon(Icons.volume_up),
          ),
          IconButton(
            tooltip: "Stop",
            onPressed: _stopAudio,
            icon: const Icon(Icons.stop),
          ),
        ],
      ),
      body: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    child: Text(
                      current.narration,
                      style: const TextStyle(fontSize: 16, height: 1.5),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Column(
                  children: List.generate(displayedChoices.length, (i) {
                    final choice = displayedChoices[i];
                    final meta = _colorForIndex(i);
                    final isDisabled = _isChoiceDisabled(choice.id);

                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: (_loading || isDisabled) ? null : () => _choose(choice.id),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: isDisabled ? Colors.grey.shade400 : meta.color,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14),
                            ),
                          ),
                          child: Stack(
                            children: [
                              Padding(
                                padding: const EdgeInsets.only(right: 44),
                                child: Align(
                                  alignment: Alignment.center,
                                  child: Text(
                                    choice.text,
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: 15,
                                      color: isDisabled ? Colors.white70 : Colors.white,
                                    ),
                                  ),
                                ),
                              ),
                              Positioned(
                                top: -10,
                                right: -10,
                                child: IconButton(
                                  tooltip: "Relire l'option",
                                  icon: const Icon(Icons.volume_up),
                                  color: Colors.white,
                                  onPressed: (_audioLoading || isDisabled)
                                      ? null
                                      : () => _playOptionAudio(i, choice),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ],
            ),
          ),
          if (_loading)
            Container(
              color: Colors.black.withOpacity(0.08),
              child: const Center(child: CircularProgressIndicator()),
            ),
        ],
      ),
    );
  }
}

class _ChoiceColor {
  final String spokenName;
  final Color color;
  const _ChoiceColor(this.spokenName, this.color);
}
