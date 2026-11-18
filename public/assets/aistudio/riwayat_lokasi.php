<?php
declare(strict_types=1);

/**
 * Endpoint API: riwayat_lokasi.php
 * Lokasi: /sales-gps-dashboard/api/riwayat_lokasi.php
 *
 * Mendukung dual-authentication:
 * 1. Administrator via PHP Session (SGPSSESSID / require_login_api) -> Akses riwayat seluruh armada.
 * 2. Salesman via Header "Authorization: Bearer <token>" -> Akses dibatasi untuk riwayat salesman bersangkutan.
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

// 4. Parameter Filter: Tanggal (dari/sampai) & Batas (limit)
$dari = isset($_GET['dari']) && preg_match('/^\d{4}-\d{2}-\d{2}$/', (string)$_GET['dari'])
    ? (string)$_GET['dari'] . ' 00:00:00'
    : date('Y-m-d') . ' 00:00:00';

$sampai = isset($_GET['sampai']) && preg_match('/^\d{4}-\d{2}-\d{2}$/', (string)$_GET['sampai'])
    ? (string)$_GET['sampai'] . ' 23:59:59'
    : date('Y-m-d') . ' 23:59:59';

$limit = isset($_GET['limit']) ? max(1, min(5000, (int)$_GET['limit'])) : 2000;

// Filter Kepemilikan Data:
// Jika Salesman: dibatasi HANYA untuk kode salesman bersangkutan
// Jika Admin: dapat melihat semua atau memfilter berdasarkan query string
$targetSales = null;
if (!$isAdmin && $salesUser) {
    $targetSales = $salesUser['kode_salesman'];
} elseif ($isAdmin && !empty($_GET['kode_salesman'])) {
    $targetSales = strtoupper(trim((string)$_GET['kode_salesman']));
}

$deviceId = !empty($_GET['device_id']) ? trim((string)$_GET['device_id']) : null;

// Query pembacaan riwayat
if ($targetSales !== null && $deviceId !== null) {
    $sql = "SELECT r.device_id, r.kode_salesman, r.latitude, r.longitude, r.akurasi, r.kecepatan, r.waktu_gps, r.diterima_pada
            FROM gps_riwayat_lokasi r
            WHERE r.kode_salesman = ? AND r.device_id = ? AND r.waktu_gps >= ? AND r.waktu_gps <= ?
            ORDER BY r.waktu_gps ASC LIMIT ?";
    $st = $localConn->prepare($sql);
    $st->bind_param('ssssi', $targetSales, $deviceId, $dari, $sampai, $limit);
} elseif ($targetSales !== null) {
    $sql = "SELECT r.device_id, r.kode_salesman, r.latitude, r.longitude, r.akurasi, r.kecepatan, r.waktu_gps, r.diterima_pada
            FROM gps_riwayat_lokasi r
            WHERE r.kode_salesman = ? AND r.waktu_gps >= ? AND r.waktu_gps <= ?
            ORDER BY r.waktu_gps ASC LIMIT ?";
    $st = $localConn->prepare($sql);
    $st->bind_param('sssi', $targetSales, $dari, $sampai, $limit);
} elseif ($deviceId !== null) {
    $sql = "SELECT r.device_id, r.kode_salesman, r.latitude, r.longitude, r.akurasi, r.kecepatan, r.waktu_gps, r.diterima_pada
            FROM gps_riwayat_lokasi r
            WHERE r.device_id = ? AND r.waktu_gps >= ? AND r.waktu_gps <= ?
            ORDER BY r.waktu_gps ASC LIMIT ?";
    $st = $localConn->prepare($sql);
    $st->bind_param('sssi', $deviceId, $dari, $sampai, $limit);
} else {
    $sql = "SELECT r.device_id, r.kode_salesman, r.latitude, r.longitude, r.akurasi, r.kecepatan, r.waktu_gps, r.diterima_pada
            FROM gps_riwayat_lokasi r
            WHERE r.waktu_gps >= ? AND r.waktu_gps <= ?
            ORDER BY r.waktu_gps ASC LIMIT ?";
    $st = $localConn->prepare($sql);
    $st->bind_param('ssi', $dari, $sampai, $limit);
}

$st->execute();
$res = $st->get_result();

// Pengelompokan titik berdasarkan device_id / kode_salesman
$groups = [];
while ($row = $res->fetch_assoc()) {
    $devKey = $row['device_id'] ?: ($row['kode_salesman'] ?: 'unknown');
    if (!isset($groups[$devKey])) {
        $groups[$devKey] = [
            'device_id'     => $row['device_id'],
            'kode_salesman' => $row['kode_salesman'],
            'titik'         => []
        ];
    }
    $groups[$devKey]['titik'][] = [
        'latitude'      => (float)($row['latitude'] ?? 0),
        'longitude'     => (float)($row['longitude'] ?? 0),
        'akurasi'       => isset($row['akurasi']) ? (float)$row['akurasi'] : null,
        'kecepatan'     => isset($row['kecepatan']) ? (float)$row['kecepatan'] : null,
        'waktu_gps'     => $row['waktu_gps'] ?? null,
        'diterima_pada' => $row['diterima_pada'] ?? null
    ];
}
$st->close();

http_response_code(200);
echo json_encode([
    'success' => true,
    'message' => 'Data riwayat lokasi berhasil diambil.',
    'data'    => array_values($groups)
]);
