<?php
declare(strict_types=1);

/**
 * Endpoint API: lokasi_terakhir.php
 * Lokasi: /sales-gps-dashboard/api/lokasi_terakhir.php
 *
 * Mendukung dual-authentication:
 * 1. Administrator via PHP Session (SGPSSESSID / require_login_api) -> Akses seluruh data perangkat.
 * 2. Salesman via Header "Authorization: Bearer <token>" -> Divalidasi dengan rkm_auth(), akses terotorisasi.
 */

date_default_timezone_set('Asia/Jakarta');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');

// Sertakan rkm_common.inc.php jika tersedia
$commonFile = __DIR__ . '/rkm_common.inc.php';
if (!file_exists($commonFile)) {
    $commonFile = dirname(__DIR__) . '/endpoint/rkm_common.inc.php';
}
if (file_exists($commonFile)) {
    require_once $commonFile;
}

// Koneksi database
require_once dirname(__DIR__, 2) . '/gpscon.php';
if (!isset($localConn) || !($localConn instanceof mysqli)) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Koneksi database tidak tersedia.']);
    exit;
}
mysqli_set_charset($localConn, 'utf8mb4');

// 1. Cek Autentikasi Administrator via Session
$isAdmin = false;
if (session_status() === PHP_SESSION_NONE) {
    @session_start();
}
if (isset($_SESSION['user_id']) || isset($_SESSION['admin_logged_in']) || isset($_SESSION['username'])) {
    $isAdmin = true;
}

// 2. Cek Autentikasi Salesman via Bearer Token
$salesUser = null;
$headers = function_exists('getallheaders') ? getallheaders() : [];
$authorization = '';
foreach ($headers as $k => $v) {
    if (strtolower((string)$k) === 'authorization') {
        $authorization = (string)$v;
        break;
    }
}
if (!$authorization && isset($_SERVER['HTTP_AUTHORIZATION'])) {
    $authorization = (string)$_SERVER['HTTP_AUTHORIZATION'];
}

if (!$isAdmin && preg_match('/^Bearer\s+([A-Za-z0-9_-]{40,200})$/i', trim($authorization), $m)) {
    $hash = hash('sha256', $m[1]);
    $sql = "SELECT t.id AS token_id, t.kode_salesman, t.expires_at, u.nama_salesman
            FROM rkm_api_token t
            JOIN rkm_user u ON u.kode_salesman = t.kode_salesman
            WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > NOW() AND u.aktif = 1
            LIMIT 1";
    $st = $localConn->prepare($sql);
    if ($st) {
        $st->bind_param('s', $hash);
        $st->execute();
        $res = $st->get_result();
        $salesUser = $res->fetch_assoc();
        $st->close();
    }
}

// 3. Verifikasi Hak Akses
if (!$isAdmin && !$salesUser) {
    http_response_code(401);
    echo json_encode([
        'success' => false,
        'message' => 'Unauthorized: Autentikasi diperlukan. Gunakan session administrator atau Bearer token salesman yang valid.'
    ]);
    exit;
}

// 4. Pengambilan Data Lokasi Terakhir
// Salesman dapat melihat lokasi armada aktif / lokasinya sendiri
$targetSales = null;
if (!$isAdmin && $salesUser) {
    // Salesman terautentikasi: default melihat lokasinya sendiri, atau armada jika ditentukan
    $reqSales = isset($_GET['kode_salesman']) ? strtoupper(trim((string)$_GET['kode_salesman'])) : '';
    $targetSales = $salesUser['kode_salesman'];
}

if ($targetSales !== null && !$isAdmin) {
    $query = "SELECT l.device_id, l.kode_salesman, l.latitude, l.longitude, l.akurasi, l.kecepatan,
                     l.waktu_gps, l.diterima_pada, l.aktif, u.nama_salesman
              FROM gps_lokasi_terakhir l
              LEFT JOIN rkm_user u ON u.kode_salesman = l.kode_salesman
              WHERE l.kode_salesman = ?
              ORDER BY l.waktu_gps DESC";
    $st = $localConn->prepare($query);
    $st->bind_param('s', $targetSales);
    $st->execute();
    $result = $st->get_result();
} else {
    // Admin: mengambil seluruh data lokasi terakhir
    $query = "SELECT l.device_id, l.kode_salesman, l.latitude, l.longitude, l.akurasi, l.kecepatan,
                     l.waktu_gps, l.diterima_pada, l.aktif, u.nama_salesman
              FROM gps_lokasi_terakhir l
              LEFT JOIN rkm_user u ON u.kode_salesman = l.kode_salesman
              ORDER BY l.waktu_gps DESC";
    $result = $localConn->query($query);
}

$data = [];
if ($result) {
    while ($row = $result->fetch_assoc()) {
        $data[] = [
            'device_id'     => $row['device_id'] ?? null,
            'kode_salesman' => $row['kode_salesman'] ?? null,
            'nama_salesman' => $row['nama_salesman'] ?? null,
            'latitude'      => (float)($row['latitude'] ?? 0),
            'longitude'     => (float)($row['longitude'] ?? 0),
            'akurasi'       => isset($row['akurasi']) ? (float)$row['akurasi'] : null,
            'kecepatan'     => isset($row['kecepatan']) ? (float)$row['kecepatan'] : null,
            'waktu_gps'     => $row['waktu_gps'] ?? null,
            'diterima_pada' => $row['diterima_pada'] ?? null,
            'aktif'         => (bool)($row['aktif'] ?? true)
        ];
    }
}

http_response_code(200);
echo json_encode([
    'success' => true,
    'message' => 'Data lokasi terakhir berhasil diambil.',
    'data'    => $data
]);
