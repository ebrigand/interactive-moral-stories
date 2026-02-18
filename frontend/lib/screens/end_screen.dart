import 'package:flutter/material.dart';
import '../services/api_service.dart';
import 'story_screen.dart';

class EndScreen extends StatelessWidget {
  final ApiService api;

  const EndScreen({required this.api, super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Fin de l'histoire")),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              "L'histoire s'est arrêtée.\nVeux-tu réessayer ?",
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 18),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              child: const Text("Revenir en arrière"),
              onPressed: () async {
                final segment = await api.rewind();
                Navigator.pushReplacement(
                  context,
                  MaterialPageRoute(
                    builder: (_) => StoryScreen(api: api, segment: segment),
                  ),
                );
              },
            )
          ],
        ),
      ),
    );
  }
}
