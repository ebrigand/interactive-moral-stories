import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

void main() {
  runApp(const iStoryApp());
}

class iStoryApp extends StatelessWidget {
  const iStoryApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'iStory',
      theme: ThemeData(useMaterial3: true),
      home: HomeScreen(),
    );
  }
}
