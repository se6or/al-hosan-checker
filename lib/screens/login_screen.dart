import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import '../services/checker_service.dart';
import 'result_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _hostController = TextEditingController();
  final TextEditingController _userController = TextEditingController();
  final TextEditingController _passController = TextEditingController();
  bool _isLoading = false;
  bool _obscurePass = true;

  void _handleCheck() async {
    if (_hostController.text.isEmpty || _userController.text.isEmpty || _passController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('يرجى ملء جميع الحقول')),
      );
      return;
    }

    setState(() => _isLoading = true);
    final result = await CheckerService.checkXtream(
      _hostController.text,
      _userController.text,
      _passController.text,
    );
    setState(() => _isLoading = false);

    if (result != null) {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (context) => ResultScreen(subscription: result)),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('فشل الفحص، تأكد من البيانات')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            children: [
              const SizedBox(height: 40),
              const Icon(FontAwesomeIcons.horseHead, size: 80, color: Color(0xFFD4AF37)),
              const SizedBox(height: 10),
              const Text(
                'محرك الحصان الفاحص',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Color(0xFFD4AF37)),
              ),
              const SizedBox(height: 40),
              _buildInput(_hostController, 'السيرفر (Host)', Icons.server),
              const SizedBox(height: 15),
              _buildInput(_userController, 'اسم المستخدم', Icons.person),
              const SizedBox(height: 15),
              _buildInput(
                _passController, 
                'كلمة المرور', 
                Icons.lock, 
                isPass: true,
                obscure: _obscurePass,
                onToggle: () => setState(() => _obscurePass = !_obscurePass),
              ),
              const Spacer(),
              ElevatedButton(
                onPressed: _isLoading ? null : _handleCheck,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFD4AF37),
                  foregroundColor: Colors.black,
                  minimumSize: const Size(double.infinity, 60),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                ),
                child: _isLoading 
                  ? const CircularProgressIndicator(color: Colors.black)
                  : const Text('بدء فحص الحصان', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInput(TextEditingController controller, String hint, IconData icon, {bool isPass = false, bool obscure = false, VoidCallback? onToggle}) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      decoration: InputDecoration(
        hintText: hint,
        prefixIcon: Icon(icon, color: const Color(0xFFD4AF37)),
        suffixIcon: isPass ? IconButton(icon: Icon(obscure ? Icons.visibility : Icons.visibility_off), onPressed: onToggle) : null,
        filled: true,
        fillColor: const Color(0xFF0A0A0A),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(15), borderSide: const BorderSide(color: Color(0xFF1F1A0F))),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(15), borderSide: const BorderSide(color: Color(0xFF1F1A0F))),
      ),
    );
  }
}
