import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'screens/login_screen.dart';

void main() {
  runApp(const AlHosanApp());
}

class AlHosanApp extends StatelessWidget {
  const AlHosanApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AlHosan Checker',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        primaryColor: const Color(0xFFD4AF37),
        textTheme: GoogleFonts.tajawalTextTheme(ThemeData.dark().textTheme),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFD4AF37),
          secondary: Color(0xFFD4AF37),
          surface: Color(0xFF0A0A0A),
        ),
      ),
      home: const LoginScreen(),
    );
  }
}
