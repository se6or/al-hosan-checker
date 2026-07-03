import 'package:flutter/material.dart';
import '../models/subscription.dart';

class ResultScreen extends StatelessWidget {
  final Subscription subscription;
  const ResultScreen({super.key, required this.subscription});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('تفاصيل الاشتراك'),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: ListView(
          children: [
            _buildInfoCard('السيرفر', subscription.host),
            _buildInfoCard('اسم المستخدم', subscription.username),
            _buildInfoCard('كلمة المرور', subscription.password),
            _buildInfoCard('تاريخ الانتهاء', subscription.expiry ?? '--'),
            _buildInfoCard('الحالة', subscription.status ?? '--', isStatus: true),
            _buildInfoCard('تجريبي', subscription.isTrial ? 'نعم' : 'لا'),
            _buildInfoCard('الأجهزة المتصلة', '${subscription.activeCons} / ${subscription.maxCons}'),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoCard(String label, String value, {bool isStatus = false}) {
    Color valColor = Colors.white;
    if (isStatus) {
      valColor = value.toLowerCase() == 'active' ? Colors.green : Colors.red;
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: const Color(0xFF0A0A0A),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: const Color(0xFF1F1A0F)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(value, style: TextStyle(color: valColor, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
