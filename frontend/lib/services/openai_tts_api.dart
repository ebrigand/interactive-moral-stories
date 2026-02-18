import 'dart:typed_data';
import 'dart:convert';
import 'package:http/http.dart' as http;

class OpenAiTtsApi {
  final String baseUrl; // ex: http://10.0.2.2:8080

  OpenAiTtsApi(this.baseUrl);

  Future<Uint8List> synthesize({
    required String text,
    String voice = "alloy",
    String format = "mp3",
  }) async {
    final uri = Uri.parse("$baseUrl/api/tts");

    final res = await http.post(
      uri,
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({
        "text": text,
        "voice": voice,
        "format": format,
      }),
    );

    if (res.statusCode >= 400) {
      throw Exception("TTS failed ${res.statusCode}: ${res.body}");
    }

    return res.bodyBytes;
  }
}
