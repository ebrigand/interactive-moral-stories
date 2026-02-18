import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../models/story_segment.dart';
import '../models/choice.dart';

class ApiService {
  /// Android emulator: http://10.0.2.2:8080
  /// iOS simulator: http://localhost:8080
  /// phone: http://IP_DE_TON_MAC:8080
  final String baseUrl;

  /// Locale demandée au backend pour le TTS (par défaut: fr-FR)
  final String ttsLocale;

  String? _sessionId;

  ApiService({this.baseUrl = "http://10.0.2.2:8080", this.ttsLocale = "fr-FR"});

  String? get sessionId => _sessionId;

  String? _playerName;
  String? get playerName => _playerName;

  Future<StorySegment> startStory({
    required int targetAge,
    required String playerName,
    required String theme,
    required int chapterCount,
  }) async {
    _playerName = playerName;
    final uri = Uri.parse("$baseUrl/api/story/start");

    final res = await http.post(
      uri,
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({
        "targetAge": targetAge,
        "playerName": playerName,
        "theme": theme,
        "chapterCount": chapterCount,
      }),
    );

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw Exception("startStory failed: ${res.statusCode} ${res.body}");
    }

    final map = jsonDecode(res.body) as Map<String, dynamic>;
    final seg = StorySegment.fromJson(map, playerName: playerName);

    _sessionId = seg.sessionId;
    return seg;
  }

  Future<StorySegment> choose(String choice) async {
    final sid = _sessionId;
    if (sid == null) {
      throw Exception("No sessionId. Call startStory first.");
    }

    final uri = Uri.parse("$baseUrl/api/story/$sid/choose");

    final res = await http.post(
      uri,
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({"choice": choice}),
    );

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw Exception("choose failed: ${res.statusCode} ${res.body}");
    }

    final map = jsonDecode(res.body) as Map<String, dynamic>;
    return StorySegment.fromJson(map, playerName: _playerName);
  }

  Future<StorySegment> rewind() async {
    if (_sessionId == null) {
      throw Exception("No sessionId (startStory not called?)");
    }

    final uri = Uri.parse("$baseUrl/api/story/$_sessionId/rewind");
    final res = await http.post(uri);

    if (res.statusCode >= 400) {
      throw Exception("Rewind failed ${res.statusCode}: ${res.body}");
    }

    final decoded = jsonDecode(res.body);

    // Cas attendu: backend renvoie RewindResponse (top-level)
    if (decoded is Map<String, dynamic> && decoded.containsKey("narration")) {
      final map = decoded;

      return StorySegment(
        sessionId: (map["sessionId"] ?? _sessionId) as String,
        title: (map["title"] ?? "") as String,
        narration: (map["narration"] ?? "") as String,
        choices: ((map["choices"] as List?) ?? const [])
            .map((e) => Choice.fromJson(e as Map<String, dynamic>))
            .toList(),
        ended: false,
        explanation: "",

        livesRemaining: (map["livesRemaining"] ?? 0) as int,
        livesTotal: (map["livesTotal"] ?? 0) as int,

        segmentIndex: (map["segmentIndex"] ?? 0) as int,
        plannedSegments: (map["plannedSegments"] ?? 1) as int,
        displaySegmentIndex: (map["segmentIndex"] ?? 0) as int,

        disabledChoiceIds: ((map["disabledChoiceIds"] as List?) ?? const [])
            .map((e) => e.toString())
            .toList(),

        // RewindResponse ne renvoie pas les utterances -> vide
        utterances: const [],
      );
    }

    // Compat éventuelle: si un jour le backend renvoie un StorySegmentResponse classique
    if (decoded is Map<String, dynamic>) {
      return StorySegment.fromJson(decoded, playerName: _playerName);
    }

    throw Exception("Unexpected rewind response: ${res.body}");
  }


  Future<Uint8List> tts(String text) async {
    final sid = _sessionId;
    if (sid == null) {
      throw Exception("No sessionId. Call startStory first.");
    }

    final uri = Uri.parse("$baseUrl/api/story/$sid/tts");
    final res = await http.post(
      uri,
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({"text": text, "locale": ttsLocale}),
    );

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw Exception("tts failed: ${res.statusCode} ${res.body}");
    }

    return res.bodyBytes;
  }
}
