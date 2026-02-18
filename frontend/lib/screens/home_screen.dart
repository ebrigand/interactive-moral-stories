import 'package:flutter/material.dart';
import '../services/api_service.dart';
import 'story_screen.dart';

class HomeScreen extends StatelessWidget {
  HomeScreen({super.key});

  final ApiService api = ApiService();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("iStory")),
      body: Center(
        child: ElevatedButton(
          child: const Text("Commencer une histoire"),
          onPressed: () async {
            final segment = await api.startStory();
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => StoryScreen(api: api, segment: segment),
              ),
            );
          },
        ),
      ),
    );
  }
}
