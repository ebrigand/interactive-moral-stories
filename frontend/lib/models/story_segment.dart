import 'choice.dart';
import 'utterance.dart';

class StorySegment {
  final String sessionId;

  final String title;
  final String narration;
  final List<Choice> choices;
  final bool ended;
  final String explanation;

  final int livesRemaining;
  final int livesTotal;

  /// Segment “backend” index (0-based).
  final int segmentIndex;

  /// Nombre de chapitres prévus (>=1).
  final int plannedSegments;

  /// Segment “affiché” (0-based) : ne bouge pas quand on arrive sur l’écran de fin.
  /// Si absent côté backend, on retombe sur segmentIndex.
  final int displaySegmentIndex;

  /// Choix à griser après un échec + rewind.
  final List<String> disabledChoiceIds;

  /// Dialogues structurés (pour les voix).
  final List<Utterance> utterances;

  StorySegment({
    required this.sessionId,
    required this.title,
    required this.narration,
    required this.choices,
    required this.ended,
    required this.explanation,
    required this.livesRemaining,
    required this.livesTotal,
    required this.segmentIndex,
    required this.plannedSegments,
    required this.displaySegmentIndex,
    required this.disabledChoiceIds,
    required this.utterances,
  });

  factory StorySegment.fromJson(Map<String, dynamic> json, {String? playerName}) {
    final sessionId = (json['sessionId'] ?? '').toString();

    final utterancesRaw = json['utterances'];
    final utterances = (utterancesRaw is List)
        ? utterancesRaw
        .map((u) => Utterance.fromJson(u as Map<String, dynamic>, playerName: playerName))
        .toList()
        : <Utterance>[];

    final segmentIndex = (json['segmentIndex'] ?? 0) as int;

    return StorySegment(
      sessionId: sessionId,
      title: (json['title'] ?? '').toString(),
      narration: (json['narration'] ?? '').toString(),
      choices: (json['choices'] as List? ?? [])
          .map((c) => Choice.fromJson(c as Map<String, dynamic>))
          .toList(),
      ended: (json['ended'] ?? false) as bool,
      explanation: (json['explanation'] ?? '').toString(),
      livesRemaining: (json['livesRemaining'] ?? 0) as int,
      livesTotal: (json['livesTotal'] ?? 0) as int,
      segmentIndex: segmentIndex,
      plannedSegments: (json['plannedSegments'] ?? 0) as int,
      displaySegmentIndex: (json['displaySegmentIndex'] ?? segmentIndex) as int,
      disabledChoiceIds: (json['disabledChoiceIds'] as List? ?? [])
          .map((e) => e.toString())
          .toList(),
      utterances: utterances,
    );
  }

  int get chapterOneBased => displaySegmentIndex + 1;
}
