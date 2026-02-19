import 'dart:async';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import '../api/client.dart';
import '../api/stream_client.dart';
import '../../main.dart';

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  CameraController? _controller;
  List<CameraDescription>? _cameras;
  String? _error;
  String? _lastResult;
  bool _processing = false;
  ApiClient? _api;
  StreamClient? _streamClient;
  int _intervalMs = 500;
  Timer? _timer;
  bool _useWebSocket = true;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    try {
      _cameras = await availableCameras();
      if (_cameras == null || _cameras!.isEmpty) {
        setState(() => _error = '카메라 없음');
        return;
      }
      final baseUrl = await ServerPrefs.getBaseUrl();
      setState(() {
        _api = ApiClient(baseUrl);
        _streamClient = StreamClient(baseUrl);
      });
      _controller = CameraController(
        _cameras!.first,
        ResolutionPreset.medium,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );
      await _controller!.initialize();
      if (!mounted) return;
      if (_useWebSocket && _streamClient != null) {
        _streamClient!.connect(
          onRecognition: (r) {
            if (!mounted) return;
            setState(() {
              if (r['matched'] == true && r['person'] != null) {
                final name = r['person']['name'] ?? '?';
                final recorded = r['attendanceRecorded'] == true;
                _lastResult = recorded ? '$name 출석 완료!' : '$name (이미 출석함)';
              } else {
                _lastResult = '미등록 사용자';
              }
            });
          },
          onConnected: () => setState(() {}),
          onError: (_) => setState(() => _lastResult = '스트림 연결 끊김'),
        );
      }
      setState(() {});
      _startRecognitionLoop();
    } catch (e) {
      setState(() => _error = e.toString());
    }
  }

  void _startRecognitionLoop() {
    _timer?.cancel();
    _timer = Timer.periodic(Duration(milliseconds: _intervalMs), (_) => _captureAndSend());
  }

  Future<void> _captureAndSend() async {
    if (_processing || _controller == null || !_controller!.value.isInitialized) return;
    setState(() => _processing = true);
    try {
      final image = await _controller!.takePicture();
      final bytes = await image.readAsBytes();
      if (_useWebSocket && _streamClient != null && _streamClient!.isConnected) {
        _streamClient!.sendImage(bytes);
        if (!mounted) return;
        setState(() => _processing = false);
      } else {
        if (_api == null) {
          setState(() => _processing = false);
          return;
        }
        final result = await _api!.recognize(bytes, 'frame.jpg');
        if (!mounted) return;
        setState(() {
          _processing = false;
          if (result['matched'] == true) {
            final name = result['person']?['name'] ?? '?';
            final recorded = result['attendanceRecorded'] == true;
            _lastResult = recorded ? '$name 출석 완료!' : '$name (이미 출석함)';
          } else {
            _lastResult = '미등록 사용자';
          }
        });
      }
    } catch (e) {
      if (mounted) setState(() { _processing = false; _lastResult = '오류: $e'; });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _streamClient?.disconnect();
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Center(child: Text('오류: $_error', style: const TextStyle(color: Colors.red)));
    }
    if (_controller == null || !_controller!.value.isInitialized) {
      return const Center(child: CircularProgressIndicator());
    }
    return Stack(
      alignment: Alignment.bottomCenter,
      children: [
        SizedBox.expand(
          child: CameraPreview(_controller!),
        ),
        Container(
          padding: const EdgeInsets.all(16),
          color: Colors.black54,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_processing) const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2)),
              const SizedBox(width: 12),
              Text(_lastResult ?? '카메라를 보세요', style: const TextStyle(fontSize: 16, color: Colors.white)),
            ],
          ),
        ),
      ],
    );
  }
}
