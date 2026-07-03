class Subscription {
  final String host;
  final String username;
  final String password;
  final String? expiry;
  final String? status;
  final String? created;
  final String? activeCons;
  final String? maxCons;
  final String? liveCount;
  final String? movieCount;
  final String? seriesCount;
  final bool isTrial;

  Subscription({
    required this.host,
    required this.username,
    required this.password,
    this.expiry,
    this.status,
    this.created,
    this.activeCons,
    this.maxCons,
    this.liveCount,
    this.movieCount,
    this.seriesCount,
    this.isTrial = false,
  });

  Map<String, dynamic> toJson() => {
    'host': host,
    'username': username,
    'password': password,
    'expiry': expiry,
    'status': status,
    'created': created,
    'activeCons': activeCons,
    'maxCons': maxCons,
    'liveCount': liveCount,
    'movieCount': movieCount,
    'seriesCount': seriesCount,
    'isTrial': isTrial,
  };

  factory Subscription.fromJson(Map<String, dynamic> json) => Subscription(
    host: json['host'],
    username: json['username'],
    password: json['password'],
    expiry: json['expiry'],
    status: json['status'],
    created: json['created'],
    activeCons: json['activeCons'],
    maxCons: json['maxCons'],
    liveCount: json['liveCount'],
    movieCount: json['movieCount'],
    seriesCount: json['seriesCount'],
    isTrial: json['isTrial'] ?? false,
  );
}
