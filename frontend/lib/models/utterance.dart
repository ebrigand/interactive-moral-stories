class Utterance {
  final String speaker;
  final String ageGroup; // "CHILD" | "ADULT"
  final String text;

  Utterance({
    required this.speaker,
    required this.ageGroup,
    required this.text,
  });

  factory Utterance.fromJson(Map<String, dynamic> json) {
    final speaker = (json['speaker'] ?? '').toString();
    final ageGroupRaw = (json['ageGroup'] ?? '').toString();

    // Fallbacks sûrs si le back/IA n’envoie pas ageGroup
    final ageGroup = ageGroupRaw.isNotEmpty
        ? ageGroupRaw
        : (speaker.toUpperCase() == 'HERO' ? 'CHILD' : 'ADULT');

    return Utterance(
      speaker: speaker,
      ageGroup: ageGroup,
      text: (json['text'] ?? '').toString(),
    );
  }
}
