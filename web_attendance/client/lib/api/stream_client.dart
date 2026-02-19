/// WebSocket 스트리밍 클라이언트 - 실시간 이미지 전송 + 인식 결과 수신
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

  /// http(s) URL을 ws(s) URL로 변환
  String get _wsUrl {
    var u = baseUrl.replaceFirst(RegExp(r'^https?://'), '');
    if (u.endsWith('/')) u = u.substring(0, u.length - 1);
    final isSecure = baseUrl.startsWith('https');
    return '${isSecure ? 'wss' : 'ws'}://$u/ws/stream';
  }

  bool get isConnected => _channel != null && !_closed;

  /// 연결
  void connect({
    required void Function(Map<String, dynamic> recognition) onRecognition,
    void Function()? onConnected,
    void Function(dynamic)? onError,
  }) {
    if (_channel != null) return;
    _closed = false;
    try {
      _channel = WebSocketChannel.connect(Uri.parse(_wsUrl));
      onConnected?.call();
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
        onError: onError,
        onDone: () {
          _channel = null;
          _sub = null;
        },
      );
    } catch (e) {
      onError?.call(e);
    }
  }

  /// 바이너리 이미지 전송
  void sendImage(List<int> bytes) {
    if (_channel == null || _closed) return;
    try {
      _channel!.sink.add(Uint8List.fromList(bytes));
    } catch (_) {}
  }

  /// 연결 종료
  void disconnect() {
    _closed = true;
    _sub?.cancel();
    _channel?.sink.close();
    _channel = null;
    _sub = null;
  }
}
