import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/subscription.dart';

class CheckerService {
  static Future<Subscription?> checkXtream(String host, String user, String pass) async {
    try {
      final url = Uri.parse('$host/player_api.php?username=$user&password=$pass');
      final response = await http.get(url).timeout(const Duration(seconds: 15));

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        if (data['user_info'] != null) {
          final userInfo = data['user_info'];
          final serverInfo = data['server_info'];
          
          return Subscription(
            host: host,
            username: user,
            password: pass,
            status: userInfo['status'] ?? 'Unknown',
            expiry: userInfo['exp_date'] != null 
                ? DateTime.fromMillisecondsSinceEpoch(int.parse(userInfo['exp_date']) * 1000).toString().split(' ')[0]
                : 'Unlimited',
            created: userInfo['created_at'] != null
                ? DateTime.fromMillisecondsSinceEpoch(int.parse(userInfo['created_at']) * 1000).toString().split(' ')[0]
                : 'Unknown',
            activeCons: userInfo['active_cons']?.toString() ?? '0',
            maxCons: userInfo['max_connections']?.toString() ?? '0',
            isTrial: userInfo['is_trial'] == "1",
            liveCount: '?', // Usually needs another call or is in different API
            movieCount: '?',
            seriesCount: '?',
          );
        }
      }
    } catch (e) {
      print('Error checking Xtream: $e');
    }
    return null;
  }
}
