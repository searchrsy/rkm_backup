<?php
declare(strict_types=1);

date_default_timezone_set('Asia/Jakarta');

function rkm_json_response(int $status, array $payload): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store, no-cache, must-revalidate');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function rkm_db(): mysqli {
    require_once dirname(__DIR__, 2) . '/gpscon.php';
    if (!isset($localConn) || !($localConn instanceof mysqli)) {
        rkm_json_response(500, ['success'=>false,'message'=>'Koneksi database tidak tersedia.']);
    }
    mysqli_set_charset($localConn, 'utf8mb4');
    return $localConn;
}
function rkm_read_json(): array {
    $raw = file_get_contents('php://input');
    $data = json_decode($raw ?: '', true);
    if (!is_array($data)) rkm_json_response(400, ['success'=>false,'message'=>'Format JSON tidak valid.']);
    return $data;
}
function rkm_allowed_salesman(string $code): bool {
    return in_array(strtoupper(trim($code)), ['ADK','AFS','CLS','VLN','AGG','TTO','FGO'], true);
}
function rkm_auth(mysqli $db): array {
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $authorization = '';
    foreach ($headers as $k => $v) if (strtolower((string)$k) === 'authorization') $authorization = (string)$v;
    if (!$authorization && isset($_SERVER['HTTP_AUTHORIZATION'])) $authorization = (string)$_SERVER['HTTP_AUTHORIZATION'];
    if (!preg_match('/^Bearer\s+([A-Za-z0-9_-]{40,200})$/i', trim($authorization), $m)) {
        rkm_json_response(401, ['success'=>false,'message'=>'Token tidak valid atau telah kedaluwarsa.']);
    }
    $hash = hash('sha256', $m[1]);
    $sql = "SELECT t.id AS token_id, t.kode_salesman, t.expires_at, u.nama_salesman
            FROM rkm_api_token t
            JOIN rkm_user u ON u.kode_salesman=t.kode_salesman
            WHERE t.token_hash=? AND t.revoked_at IS NULL AND t.expires_at > NOW() AND u.aktif=1
            LIMIT 1";
    $st = $db->prepare($sql);
    if (!$st) rkm_json_response(500, ['success'=>false,'message'=>'Layanan sementara tidak tersedia.']);
    $st->bind_param('s', $hash);
    $st->execute();
    $row = $st->get_result()->fetch_assoc();
    $st->close();
    if (!$row) rkm_json_response(401, ['success'=>false,'message'=>'Token tidak valid atau telah kedaluwarsa.']);
    return $row;
}
function rkm_today(): string { return (new DateTimeImmutable('now', new DateTimeZone('Asia/Jakarta')))->format('Y-m-d'); }
function rkm_decode_list($json): array {
    if (is_array($json)) return $json;
    $v = json_decode((string)$json, true);
    return is_array($v) ? $v : [];
}
function rkm_member_code($item): string {
    if (is_string($item) || is_numeric($item)) return trim((string)$item);
    if (!is_array($item)) return '';
    foreach (['kode_member','cus_kodemember','kode','member_code','id_member'] as $k) {
        if (isset($item[$k]) && is_scalar($item[$k])) return trim((string)$item[$k]);
    }
    return '';
}
function rkm_member_name($item): string {
    if (!is_array($item)) return '';
    foreach (['nama_member','cus_namamember','nama','name'] as $k) {
        if (isset($item[$k]) && is_scalar($item[$k])) return trim((string)$item[$k]);
    }
    return '';
}
function rkm_find_coords($coordList, string $code, int $index): string {
    $list = rkm_decode_list($coordList);
    $candidate = $list[$index] ?? null;
    if (is_array($candidate)) {
        $c = rkm_member_code($candidate);
        if ($c === '' || $c === $code) {
            foreach (['koordinat','koordinat_member','coords','coordinate'] as $k) if (!empty($candidate[$k])) return (string)$candidate[$k];
            if (isset($candidate['latitude'], $candidate['longitude'])) return $candidate['latitude'].','.$candidate['longitude'];
            if (isset($candidate['lat'], $candidate['lng'])) return $candidate['lat'].','.$candidate['lng'];
        }
    } elseif (is_string($candidate) || is_numeric($candidate)) {
        return (string)$candidate;
    }
    foreach ($list as $c) {
        if (!is_array($c) || rkm_member_code($c) !== $code) continue;
        foreach (['koordinat','koordinat_member','coords','coordinate'] as $k) if (!empty($c[$k])) return (string)$c[$k];
        if (isset($c['latitude'], $c['longitude'])) return $c['latitude'].','.$c['longitude'];
        if (isset($c['lat'], $c['lng'])) return $c['lat'].','.$c['lng'];
    }
    return '';
}
function rkm_get_plan(mysqli $db, int $id, string $sales, string $date, bool $lock=false): ?array {
    $sql = "SELECT id,kode_salesman,tanggal_kunjungan,daftar_member,daftar_koordinat,status
            FROM rkm WHERE id=? AND kode_salesman=? AND tanggal_kunjungan=?";
    if ($lock) $sql .= " FOR UPDATE";
    $st=$db->prepare($sql);
    if (!$st) return null;
    $st->bind_param('iss',$id,$sales,$date);
    $st->execute(); $row=$st->get_result()->fetch_assoc(); $st->close();
    return $row ?: null;
}
