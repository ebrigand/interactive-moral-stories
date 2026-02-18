import 'choice.dart';

class StorySegment {
  final String narration;
  final List<Choice> choices;

  StorySegment({required this.narration, required this.choices});

  factory StorySegment.fromJson(Map<String, dynamic> json) {
    return StorySegment(
      narration: json['narration'],
      choices: (json['choices'] as List)
          .map((c) => Choice.fromJson(c))
          .toList(),
    );
  }
}
