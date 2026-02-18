import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/story_segment.dart';

class ApiService {
  static const String baseUrl = 'http://10.0.2.2:8080/api/story'; 
  // 10.0.2.2 = localhost pour Android emulator

  String? sessionId;

  Future<StorySegment> startStory() async {
    final response = await http.post(
      Uri.parse('$baseUrl/start'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        "targetAge": 8,
        "character": "Jeune explorateur curieux",
        "environment": "Forêt mystérieuse",
        "mission": "Aider quelqu’un en danger",
        "tone": "Aventure et bienveillance"
      }),
    );

    final json = jsonDecode(response.body);
    sessionId ??= json['sessionId']; // si tu ajoutes sessionId plus tard
    return StorySegment.fromJson(json);
  }

  Future<StorySegment> sendChoice(String choiceId) async {
    final response = await http.post(
      Uri.parse('$baseUrl/$sessionId/choice'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({"choiceId": choiceId}),
    );

    return StorySegment.fromJson(jsonDecode(response.body));
  }

  Future<StorySegment> rewind() async {
    final response = await http.post(
      Uri.parse('$baseUrl/$sessionId/rewind'),
    );

    return StorySegment.fromJson(jsonDecode(response.body));
  }
}
