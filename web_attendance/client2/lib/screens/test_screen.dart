import 'dart:async';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';
import '../api/test_client.dart';
import '../api/stream_client.dart';
import '../../main.dart';

class TestScreen extends StatefulWidget {
  const TestScreen({super.key});

  @override
  State<TestScreen> createState() => _TestScreenState();
}

class _TestScreenState extends State<TestScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _urlController = TextEditingController();
  String _log = '';
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadUrl();
  }

  Future<void> _loadUrl() async {
    _urlController.text = await ServerPrefs.getBaseUrl();
  }

  void _logMsg(String msg) {
    setState(() => _log = '$_log\n$msg');
  }

  void _clearLog() => setState(() => _log = '');

  String get _baseUrl => _urlController.text.trim();

  Future<void> _saveUrl() async {
    await ServerPrefs.setBaseUrl(_baseUrl);
    _logMsg('URL 저장됨: $_baseUrl');
  }

  @override
  void dispose() {
    _tabController.dispose();
    _urlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('얼굴 출석 테스트'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'API 테스트'),
            Tab(text: '스트림 테스트'),
            Tab(text: '등록/인식'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildApiTestTab(),
          _buildStreamTestTab(),
          _buildRegisterTab(),
        ],
      ),
    );
  }

  Widget _buildApiTestTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _urlController,
            decoration: const InputDecoration(labelText: '서버 URL'),
            keyboardType: TextInputType.url,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              ElevatedButton(onPressed: _saveUrl, child: const Text('저장')),
              const SizedBox(width: 8),
              TextButton(onPressed: _clearLog, child: const Text('로그 지우기')),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildTestBtn('Health', () async {
                setState(() => _loading = true);
                try {
                  final c = TestClient(_baseUrl);
                  final r = await c.health();
                  _logMsg('Health: ${r.toString()}');
                } catch (e) {
                  _logMsg('Health 오류: $e');
                }
                setState(() => _loading = false);
              }),
              _buildTestBtn('Persons', () async {
                setState(() => _loading = true);
                try {
                  final c = TestClient(_baseUrl);
                  final r = await c.getPersons();
                  _logMsg('Persons: ${r.length}명');
                } catch (e) {
                  _logMsg('Persons 오류: $e');
                }
                setState(() => _loading = false);
              }),
              _buildTestBtn('Attendances', () async {
                setState(() => _loading = true);
                try {
                  final c = TestClient(_baseUrl);
                  final r = await c.getAttendances();
                  _logMsg('Attendances: ${r.length}건');
                } catch (e) {
                  _logMsg('Attendances 오류: $e');
                }
                setState(() => _loading = false);
              }),
            ],
          ),
          const SizedBox(height: 16),
          if (_loading) const LinearProgressIndicator(),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.black87,
              borderRadius: BorderRadius.circular(8),
            ),
            constraints: const BoxConstraints(maxHeight: 200),
            child: SingleChildScrollView(
              child: Text(_log.isEmpty ? '(로그 없음)' : _log, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTestBtn(String label, VoidCallback onPressed) {
    return ElevatedButton(
      onPressed: _loading ? null : onPressed,
      child: Text(label),
    );
  }

  Widget _buildStreamTestTab() {
    return _StreamTestTab(baseUrl: _baseUrl, onLog: _logMsg);
  }

  Widget _buildRegisterTab() {
    return _RegisterTab(baseUrl: _baseUrl, onLog: _logMsg);
  }
}

class _StreamTestTab extends StatefulWidget {
  final String baseUrl;
  final void Function(String) onLog;

  const _StreamTestTab({required this.baseUrl, required this.onLog});

  @override
  State<_StreamTestTab> createState() => _StreamTestTabState();
}

class _StreamTestTabState extends State<_StreamTestTab> {
  CameraController? _controller;
  List<CameraDescription>? _cameras;
  StreamClient? _streamClient;
  int _frameCount = 0;
  String? _lastResult;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    try {
      _cameras = await availableCameras();
      if (_cameras == null || _cameras!.isEmpty) {
        widget.onLog('카메라 없음');
        return;
      }
      _controller = CameraController(
        _cameras!.first,
        ResolutionPreset.medium,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );
      await _controller!.initialize();
      if (!mounted) return;
      setState(() {});
      _streamClient = StreamClient(widget.baseUrl);
      _streamClient!.connect(
        onRecognition: (r) {
          if (!mounted) return;
          setState(() {
            if (r['matched'] == true && r['person'] != null) {
              _lastResult = '${r['person']['name']} ${r['attendanceRecorded'] == true ? '(출석)' : ''}';
            } else {
              _lastResult = '미등록';
            }
          });
        },
        onConnected: () {
          widget.onLog('WebSocket 연결됨');
          _startStream();
        },
        onError: (e) {
          widget.onLog('WS 오류: $e');
          widget.onLog('서버 URL: ${widget.baseUrl}');
          widget.onLog('WebSocket URL: ${_streamClient?.wsUrl ?? "N/A"}');
          widget.onLog('휴대폰 사용 시: PC IP 주소 사용 (예: http://192.168.0.201:8000)');
        },
      );
    } catch (e) {
      widget.onLog('초기화 오류: $e');
    }
  }

  void _startStream() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(milliseconds: 500), (_) => _sendFrame());
  }

  Future<void> _sendFrame() async {
    if (_controller == null || !_controller!.value.isInitialized || _streamClient == null || !_streamClient!.isConnected) return;
    try {
      final img = await _controller!.takePicture();
      final bytes = await img.readAsBytes();
      _streamClient!.sendImage(bytes);
      if (!mounted) return;
      setState(() => _frameCount++);
    } catch (_) {}
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
    if (_controller == null || !_controller!.value.isInitialized) {
      return const Center(child: CircularProgressIndicator());
    }
    return Stack(
      alignment: Alignment.bottomCenter,
      children: [
        SizedBox.expand(child: CameraPreview(_controller!)),
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            color: Colors.black54,
            child: Text(
              '서버: ${widget.baseUrl.isEmpty ? "(API 탭에서 URL 설정)" : widget.baseUrl}',
              style: const TextStyle(color: Colors.white70, fontSize: 11),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ),
        Container(
          padding: const EdgeInsets.all(16),
          color: Colors.black54,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('프레임: $_frameCount', style: const TextStyle(color: Colors.white)),
              const SizedBox(width: 16),
              Text(_lastResult ?? '-', style: const TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold)),
            ],
          ),
        ),
      ],
    );
  }
}

class _RegisterTab extends StatefulWidget {
  final String baseUrl;
  final void Function(String) onLog;

  const _RegisterTab({required this.baseUrl, required this.onLog});

  @override
  State<_RegisterTab> createState() => _RegisterTabState();
}

class _RegisterTabState extends State<_RegisterTab> {
  final _nameController = TextEditingController();
  final List<File> _images = [];
  final _picker = ImagePicker();
  bool _loading = false;

  Future<void> _pickImages() async {
    final files = await _picker.pickMultiImage();
    if (files.isEmpty) return;
    setState(() {
      for (final x in files) {
        _images.add(File(x.path));
      }
      if (_images.length > 5) _images.removeRange(5, _images.length);
    });
  }

  Future<void> _register() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      widget.onLog('이름 입력');
      return;
    }
    if (_images.isEmpty) {
      widget.onLog('사진 1장 이상');
      return;
    }
    setState(() => _loading = true);
    try {
      final c = TestClient(widget.baseUrl);
      await c.register(name, _images);
      widget.onLog('등록 완료: $name');
      if (!mounted) return;
      setState(() {
        _loading = false;
        _nameController.clear();
        _images.clear();
      });
    } catch (e) {
      widget.onLog('등록 오류: $e');
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _recognize() async {
    if (_images.isEmpty) {
      widget.onLog('사진 먼저 선택');
      return;
    }
    setState(() => _loading = true);
    try {
      final bytes = await _images.first.readAsBytes();
      final c = TestClient(widget.baseUrl);
      final r = await c.recognize(bytes);
      widget.onLog('인식: ${r.toString()}');
      if (!mounted) return;
      setState(() => _loading = false);
    } catch (e) {
      widget.onLog('인식 오류: $e');
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(labelText: '이름'),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _loading ? null : _pickImages,
            icon: const Icon(Icons.add_photo_alternate),
            label: Text('사진 추가 (${_images.length}/5)'),
          ),
          if (_images.isNotEmpty)
            SizedBox(
              height: 80,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                itemCount: _images.length,
                itemBuilder: (_, i) => Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: Image.file(_images[i], fit: BoxFit.cover, width: 60, height: 60),
                ),
              ),
            ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _loading ? null : _register,
                  child: const Text('등록'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton(
                  onPressed: _loading ? null : _recognize,
                  child: const Text('인식 테스트'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
