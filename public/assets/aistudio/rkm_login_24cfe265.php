<?php
declare(strict_types=1);
require_once __DIR__ . '/rkm_common.inc.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    rkm_json_response(405, ['success'=>false,'message'=>'Metode tidak diizinkan.']);
}
if (stripos($_SERVER['CONTENT_TYPE'] ?? '', 'application/json') === false) {
    rkm_json_response(415, ['success'=>false,'message'=>'Gunakan Content-Type application/json.']);
}
$data = rkm_read_json();
$code = strtoupper(trim((string)($data['kode_salesman'] ?? '')));
$password = (string)($data['password'] ?? '');
if (!rkm_allowed_salesman($code) || $password === '' || strlen($password) > 1024) {
    rkm_json_response(401, ['success'=>false,'message'=>'Kode salesman atau password salah.']);
}
$db = rkm_db();

/* Rate limit berbasis kode + IP; tabel menyimpan hash identitas, bukan password/token. */
$ip = (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown');
$bucket = hash('sha256', strtolower($code).'|'.$ip);
$st = $db->prepare("SELECT attempts, window_started FROM rkm_login_limit WHERE bucket_hash=?");
if (!$st) rkm_json_response(500, ['success'=>false,'message'=>'Layanan sementara tidak tersedia.']);
$st->bind_param('s',$bucket); $st->execute(); $limit=$st->get_result()->fetch_assoc(); $st->close();
$now = time();
if ($limit && strtotime((string)$limit['window_started']) > $now - 900 && (int)$limit['attempts'] >= 10) {
    rkm_json_response(429, ['success'=>false,'message'=>'Terlalu banyak percobaan. Coba lagi 15 menit kemudian.']);
}
$st = $db->prepare("INSERT INTO rkm_login_limit (bucket_hash, attempts, window_started) VALUES (?,1,NOW())
 ON DUPLICATE KEY UPDATE
 attempts=IF(window_started < DATE_SUB(NOW(), INTERVAL 15 MINUTE),1,attempts+1),
 window_started=IF(window_started < DATE_SUB(NOW(), INTERVAL 15 MINUTE),NOW(),window_started)");
if (!$st) rkm_json_response(500, ['success'=>false,'message'=>'Layanan sementara tidak tersedia.']);
$st->bind_param('s',$bucket); $st->execute(); $st->close();

$st = $db->prepare("SELECT kode_salesman,nama_salesman,password_hash,aktif FROM rkm_user WHERE kode_salesman=? LIMIT 1");
if (!$st) rkm_json_response(500, ['success'=>false,'message'=>'Layanan sementara tidak tersedia.']);
$st->bind_param('s',$code); $st->execute(); $user=$st->get_result()->fetch_assoc(); $st->close();
if (!$user || (int)$user['aktif'] !== 1 || !password_verify($password, (string)$user['password_hash'])) {
    rkm_json_response(401, ['success'=>false,'message'=>'Kode salesman atau password salah.']);
}

/* 32 byte acak; hanya hash SHA-256 yang disimpan. Token berlaku 12 jam. */
try { $token = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='); }
catch (Throwable $e) { rkm_json_response(500, ['success'=>false,'message'=>'Gagal membuat sesi aman.']); }
$tokenHash = hash('sha256',$token);
$expires = (new DateTimeImmutable('+12 hours', new DateTimeZone('Asia/Jakarta')))->format('Y-m-d H:i:s');
$st = $db->prepare("INSERT INTO rkm_api_token (kode_salesman,token_hash,expires_at,created_at) VALUES (?,?,?,NOW())");
if (!$st) rkm_json_response(500, ['success'=>false,'message'=>'Layanan sementara tidak tersedia.']);
$st->bind_param('sss',$code,$tokenHash,$expires);
if (!$st->execute()) { $st->close(); rkm_json_response(500, ['success'=>false,'message'=>'Gagal membuat sesi.']); }
$st->close();
rkm_json_response(200, ['success'=>true,'message'=>'Login berhasil','token'=>$token,'expires_at'=>$expires,
    'data'=>['kode_salesman'=>$code,'nama_salesman'=>$user['nama_salesman']]]);
