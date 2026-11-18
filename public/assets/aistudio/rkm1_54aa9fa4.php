<?php
declare(strict_types=1);
require_once __DIR__ . '/rkm_common.inc.php';
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    rkm_json_response(405,['success'=>false,'message'=>'Metode tidak diizinkan.']);
}
$db=rkm_db(); $auth=rkm_auth($db); $sales=(string)$auth['kode_salesman']; $today=rkm_today();

/* Rekonsiliasi hari yang telah berakhir dilakukan scheduler. Endpoint ini tidak membaca tanggal lain. */
$st=$db->prepare("SELECT id,kode_salesman,tanggal_kunjungan,daftar_member,daftar_koordinat,status
                  FROM rkm WHERE kode_salesman=? AND tanggal_kunjungan=? LIMIT 1");
$st->bind_param('ss',$sales,$today); $st->execute(); $plan=$st->get_result()->fetch_assoc(); $st->close();
if (!$plan) rkm_json_response(200,['success'=>true,'message'=>'Belum ada rencana kunjungan hari ini','data'=>[
    'tanggal'=>$today,'kode_salesman'=>$sales,'rencana'=>null,'kunjungan'=>[]]]);
$status=strtolower((string)$plan['status']);
if ($status !== 'aktif') {
    rkm_json_response(200,['success'=>true,'message'=>'Rencana hari ini tidak dapat dijalankan','data'=>[
        'tanggal'=>$today,'kode_salesman'=>$sales,'rencana'=>['id'=>(int)$plan['id'],'status'=>$status],'kunjungan'=>[]]]);
}
$members=rkm_decode_list($plan['daftar_member']);
$coords=$plan['daftar_koordinat'];
$st=$db->prepare("SELECT kode_member, hasil, sumber_pencatatan FROM rencana_rkm WHERE id_rkm=?");
$id=(int)$plan['id']; $st->bind_param('i',$id); $st->execute(); $rs=$st->get_result();
$done=[];
while($row=$rs->fetch_assoc()) $done[(string)$row['kode_member']]=true;
$st->close();
$visits=[]; $seen=[];
foreach($members as $i=>$item) {
    $code=rkm_member_code($item);
    if ($code==='' || isset($seen[$code])) continue;
    $seen[$code]=true;
    if (isset($done[$code])) continue;
    $name=rkm_member_name($item);
    $entry=is_array($item)?$item:[];
    $visits[] = [
        'id_rkm' => (int)$plan['id'],
        'urutan' => (int)($entry['urutan_kunjungan'] ?? $entry['urutan'] ?? ($i + 1)),
        'kode_member' => $code,
        'nama_member' => $name,
        'alamat' => (string)($entry['alamat'] ?? $entry['cus_alamat'] ?? ''),
        'kecamatan' => (string)($entry['kecamatan'] ?? $entry['cus_kecamatan'] ?? ''),
        'koordinat' => rkm_find_coords($coords, $code, (int)$i),
        'status_member' => (string)($entry['status_member'] ?? $entry['status'] ?? 'AKTIF'),
        'status_kunjungan' => 'BELUM'
    ];
}
rkm_json_response(200,['success'=>true,'message'=>'Daftar kunjungan berhasil diambil','data'=>[
    'tanggal'=>$today,'kode_salesman'=>$sales,'rencana'=>['id'=>(int)$plan['id'],'status'=>$status],'kunjungan'=>$visits]]);
