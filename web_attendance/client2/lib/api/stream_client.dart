import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:web_socket_channel/web_socket_channel.dart';

class StreamClient {
  StreamClient(this.baseUrl);
  final String baseUrl;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  bool _closed = false;

  String get _wsUrl {
    var u = baseUrl.replaceFirst(RegExp(r'^https?://'), '');
    if (u.endsWith('/')) u = u.substring(0, u.length - 1);
    return '${baseUrl.startsWith('https') ? 'wss' : 'ws'}://$u/ws/stream';
  }
  
  String get wsUrl => _wsUrl;  // 디버깅용 public getter

  bool get isConnected => _channel != null && !_closed;

  void connect({
    required void Function(Map<String, dynamic>) onRecognition,
    void Function()? onConnected,
    void Function(dynamic)? onError,
  }) {
    if (_channel != null) return;
    _closed = false;
    try {
      _channel = WebSocketChannel.connect(Uri.parse(_wsUrl));
      _sub = _channel!.stream.listen(
        (data) {
          if (_closed) return;
          if (data is String) {
            try {
              final msg = jsonDecode(data) as Map<String, dynamic>?;
              if (msg != null && msg['type'] == 'recognition' && msg['recognition'] != null) {
                onRecognition(Map<String, dynamic>.from(msg['recognition'] as Map));
              }
            } catch (_) {}
          }
        },
        onError: (error) {
          _closed = true;
          _channel = null;
          _sub = null;
          onError?.call(error);
        },
        onDone: () {
          _closed = true;
          _channel = null;
          _sub = null;
        },
      );
      Future.delayed(const Duration(milliseconds: 800), () {
        if (!_closed && _channel != null) {
          onConnected?.call();
        } else if (!_closed) {
          onError?.call('연결 시간 초과. 서버 URL을 확인하세요: $_wsUrl');
        }
      });
    } catch (e) {
      onError?.call(e);
    }
  }

  void sendImage(List<int> bytes) {
    if (_channel == null || _closed) return;
    try {
      _channel!.sink.add(Uint8List.fromList(bytes));
    } catch (_) {}
  }

  void disconnect() {
    _closed = true;
    _sub?.cancel();
    _channel?.sink.close();
    _channel = null;
    _sub = null;
  }
}
