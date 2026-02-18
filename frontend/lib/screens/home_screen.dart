import 'package:flutter/material.dart';
import '../services/api_service.dart';
import 'story_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // Android emulator: http://10.0.2.2:8080
  // iOS simulator: http://localhost:8080
  static const String backendBaseUrl = "http://10.0.2.2:8080";

  late final ApiService api = ApiService(baseUrl: backendBaseUrl);

  final _formKey = GlobalKey<FormState>();
  final TextEditingController _nameCtrl = TextEditingController(text: "Alex");

  int _age = 8;
  int _chapterCount = 8;

  final List<String> _themes = const [
    "Aventure",
    "Mystère",
    "Fantastique",
    "Espace",
    "Pirates",
    "Animaux",
    "Chevaliers",
    "Magie",
  ];
  String _theme = "Aventure";

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    if (!_formKey.currentState!.validate()) return;

    final playerName = _nameCtrl.text.trim();

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );

    try {
      final segment = await api.startStory(
        targetAge: _age,
        playerName: playerName,
        theme: _theme,
        chapterCount: _chapterCount,
      );

      if (context.mounted) Navigator.pop(context);

      if (!context.mounted) return;
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => StoryScreen(api: api, segment: segment),
        ),
      );
    } catch (e) {
      if (context.mounted) Navigator.pop(context);

      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Erreur: $e")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        centerTitle: true,
        title: const Text("iStory"),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Form(
              key: _formKey,
              child: ListView(
                children: [
                  Text(
                    "Crée ton histoire 📚",
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 18),

                  TextFormField(
                    controller: _nameCtrl,
                    decoration: const InputDecoration(
                      labelText: "Nom du héros",
                      border: OutlineInputBorder(),
                    ),
                    validator: (v) {
                      final s = (v ?? "").trim();
                      if (s.isEmpty) return "Indique un nom";
                      if (s.length < 2) return "Nom trop court";
                      return null;
                    },
                  ),

                  const SizedBox(height: 16),

                  // 🎯 Âge
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          "Âge",
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                        ),
                      ),
                      Text(
                        "$_age",
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                  Slider(
                    value: _age.toDouble(),
                    min: 3,
                    max: 16,
                    divisions: 13,
                    label: "$_age",
                    onChanged: (v) => setState(() => _age = v.round()),
                  ),

                  const SizedBox(height: 16),

                  // 🎭 Thème
                  DropdownButtonFormField<String>(
                    initialValue: _theme,
                    decoration: const InputDecoration(
                      labelText: "Thème",
                      border: OutlineInputBorder(),
                    ),
                    items: _themes
                        .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                        .toList(),
                    onChanged: (v) => setState(() => _theme = v ?? _theme),
                  ),

                  const SizedBox(height: 16),

                  // 📚 Chapitres (remplace la durée)
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          "Nombre de chapitres",
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                        ),
                      ),
                      Text(
                        "$_chapterCount chap.",
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                  Slider(
                    value: _chapterCount.toDouble(),
                    min: 4,
                    max: 20,
                    divisions: 16,
                    label: "$_chapterCount",
                    onChanged: (v) => setState(() => _chapterCount = v.round()),
                  ),

                  const SizedBox(height: 20),

                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _start,
                      child: const Text("Démarrer 🚀"),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
