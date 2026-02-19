import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

class TestClient {
  TestClient(this.baseUrl);
  String baseUrl;

  String get api => baseUrl.endsWith('/') ? '${baseUrl}api' : '$baseUrl/api';

  Future<Map<String, dynamic>> health() async {
    final r = await http.get(Uri.parse('$api/health'));
    if (r.statusCode != 200) throw Exception(r.body);
    return jsonDecode(r.body) as Map<String, dynamic>;
  }

  Future<List<dynamic>> getPersons() async {
    final r = await http.get(Uri.parse('$api/persons'));
    if (r.statusCode != 200) throw Exception(r.body);
    final d = jsonDecode(r.body) as Map<String, dynamic>;
    return (d['persons'] as List?) ?? [];
  }

  Future<List<dynamic>> getAttendances({String? date}) async {
    var path = '$api/attendances';
    if (date != null) path += '?date=$date';
    final r = await http.get(Uri.parse(path));
    if (r.statusCode != 200) throw Exception(r.body);
    final d = jsonDecode(r.body) as Map<String, dynamic>;
    return (d['attendances'] as List?) ?? [];
  }

  Future<Map<String, dynamic>> register(String name, List<File> files) async {
    final uri = Uri.parse('$api/register');
    final req = http.MultipartRequest('POST', uri);
    req.fields['name'] = name;
    for (final f in files) {
      req.files.add(await http.MultipartFile.fromPath('images', f.path));
    }
    final res = await http.Response.fromStream(await req.send());
    if (res.statusCode != 200 && res.statusCode != 201) throw Exception(res.body);
    return jsonDecode(res.body) as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> recognize(List<int> bytes) async {
    final uri = Uri.parse('$api/recognize');
    final req = http.MultipartRequest('POST', uri);
    req.files.add(http.MultipartFile.fromBytes('image', bytes, filename: 'test.jpg'));
    final res = await http.Response.fromStream(await req.send());
    if (res.statusCode != 200) throw Exception(res.body);
    return jsonDecode(res.body) as Map<String, dynamic>;
  }
}
