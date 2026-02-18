class Utterance {
  final String speaker;
  final String ageGroup; // CHILD | ADULT
  final String gender;   // MALE | FEMALE | NEUTRAL
  final String text;

  Utterance({
    required this.speaker,
    required this.ageGroup,
    required this.gender,
    required this.text,
  });

  factory Utterance.fromJson(Map<String, dynamic> json, {String? playerName}) {
    final speaker = (json['speaker'] ?? '').toString();
    final rawAge  = (json['ageGroup'] ?? '').toString();
    final rawGen  = (json['gender'] ?? '').toString().toUpperCase();

    // Le héros est toujours CHILD, quoi que l'IA retourne
    final isHero = speaker.toUpperCase() == 'HERO'
        || (playerName != null && speaker.toLowerCase() == playerName.toLowerCase());

    return Utterance(
      speaker: speaker,
      ageGroup: isHero ? 'CHILD' : (rawAge.isNotEmpty ? rawAge : 'ADULT'),
      gender: (rawGen == 'MALE' || rawGen == 'FEMALE') ? rawGen : 'NEUTRAL',
      text: (json['text'] ?? '').toString(),
    );
  }
}