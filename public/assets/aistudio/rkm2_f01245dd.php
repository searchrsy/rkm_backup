<?php
declare(strict_types=1);
require_once __DIR__ . '/rkm_common.inc.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    rkm_json_response(405,['success'=>false,'message'=>'Metode tidak diizinkan.']);
}
$db=rkm_db(); $auth=rkm_auth($db);
$sales=(string)$auth['kode_salesman']; $today=rkm_today();
$maxBytes=5*1024*1024;
if (!isset($_SERVER['CONTENT_LENGTH']) || (int)$_SERVER['CONTENT_LENGTH'] > $maxBytes + 256*1024) {
    rkm_json_response(413,['success'=>false,'message'=>'Ukuran request terlalu besar.']);
}
$idRkm=filter_var($_POST['id_rkm'] ?? null,FILTER_VALIDATE_INT);
$member=trim((string)($_POST['kode_member'] ?? ''));
$result=trim((string)($_POST['hasil'] ?? ''));
$note=trim((string)($_POST['catatan'] ?? ''));
$followRaw=trim((string)($_POST['follow_up'] ?? ''));
$key=trim((string)($_POST['idempotency_key'] ?? ''));
$latRaw=$_POST['latitude'] ?? null; $lonRaw=$_POST['longitude'] ?? null;
$accRaw=$_POST['accuracy_m'] ?? null;
if (!$idRkm || $idRkm < 1) {
    rkm_json_response(422, [
        'success' => false,
        'message' => 'id_rkm tidak valid.'
    ]);
}

if ($member === '' || strlen($member) > 50) {
    rkm_json_response(422, [
        'success' => false,
        'message' => 'kode_member tidak valid.'
    ]);
}

if (!in_array($result, $allowed, true)) {
    rkm_json_response(422, [
        'success' => false,
        'message' => 'hasil kunjungan tidak valid.'
    ]);
}

if (mb_strlen($note, 'UTF-8') > 150) {
    rkm_json_response(422, [
        'success' => false,
        'message' => 'Catatan maksimal 150 karakter.'
    ]);
}

if (!preg_match('/^[A-Za-z0-9._:-]{16,100}$/', $key)) {
    rkm_json_response(422, [
        'success' => false,
        'message' => 'idempotency_key harus terdiri dari 16–100 karakter dan menggunakan format yang diizinkan.'
    ]);
}
if ($result==='ALASAN LAIN' && $note==='') rkm_json_response(422,['success'=>false,'message'=>'Catatan wajib diisi untuk ALASAN LAIN.']);
$follow=null;
if ($followRaw!=='') {
    $dt=DateTimeImmutable::createFromFormat('!Y-m-d',$followRaw,new DateTimeZone('Asia/Jakarta'));
    if (!$dt || $dt->format('Y-m-d')!==$followRaw) rkm_json_response(422,['success'=>false,'message'=>'Tanggal follow_up tidak valid.']);
    $follow=$followRaw;
}
if (!is_numeric($latRaw) || !is_numeric($lonRaw)) rkm_json_response(422,['success'=>false,'message'=>'Koordinat GPS wajib diisi.']);
$lat=(float)$latRaw; $lon=(float)$lonRaw;
if ($lat < -90 || $lat > 90 || $lon < -180 || $lon > 180) rkm_json_response(422,['success'=>false,'message'=>'Koordinat GPS di luar rentang yang sah.']);
$accuracy=null;
if ($accRaw!==null && $accRaw!=='') {
    if (!is_numeric($accRaw) || (float)$accRaw<0 || (float)$accRaw>100000) rkm_json_response(422,['success'=>false,'message'=>'accuracy_m tidak valid.']);
    $accuracy=(float)$accRaw;
}
if (!isset($_FILES['foto']) || !is_array($_FILES['foto']) || $_FILES['foto']['error']!==UPLOAD_ERR_OK) {
    rkm_json_response(422,['success'=>false,'message'=>'Foto kunjungan wajib diunggah.']);
}
$file=$_FILES['foto'];
if (!is_uploaded_file($file['tmp_name']) || (int)$file['size']<1 || (int)$file['size']>$maxBytes) {
    rkm_json_response(413,['success'=>false,'message'=>'Foto tidak valid atau melebihi batas 5 MB.']);
}
$finfo=new finfo(FILEINFO_MIME_TYPE); $mime=$finfo->file($file['tmp_name']);
$img=@getimagesize($file['tmp_name']);
$types=['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'];
if (!$img || !isset($types[$mime]) || !isset($img[0],$img[1]) || $img[0]<1 || $img[1]<1) {
    rkm_json_response(415,['success'=>false,'message'=>'Foto harus berupa JPEG, PNG, atau WebP yang valid.']);
}
$ext=$types[$mime];
$todayDt=new DateTimeImmutable('now',new DateTimeZone('Asia/Jakarta'));
$folderDate=$todayDt->format('Y-m-d');
$base='D:/rkm/'.$sales.'/'.$folderDate;
if (!is_dir($base) && !@mkdir($base,0700,true) && !is_dir($base)) {
    rkm_json_response(500,['success'=>false,'message'=>'Penyimpanan foto tidak tersedia.']);
}
if (!is_writable($base)) rkm_json_response(500,['success'=>false,'message'=>'Direktori foto tidak dapat ditulis oleh server.']);
try { $filename=bin2hex(random_bytes(20)).'.'.$ext; }
catch(Throwable $e) { rkm_json_response(500,['success'=>false,'message'=>'Gagal membuat nama file aman.']); }
$absolute=$base.'/'.$filename;
$relative=$sales.'/'.$folderDate.'/'.$filename;
$coordJson=json_encode(['latitude'=>$lat,'longitude'=>$lon,'accuracy_m'=>$accuracy],JSON_UNESCAPED_SLASHES);
$keyHash=hash('sha256',$key);
$payloadHash=hash('sha256',json_encode([$idRkm,$member,$result,$note,$follow,$lat,$lon,$accuracy,hash_file('sha256',$file['tmp_name'])]));
$stored=false; $db->begin_transaction();
try {
    /* Lock rencana agar submit dan rekonsiliasi tidak dapat berjalan tanpa koordinasi. */
    $plan=rkm_get_plan($db,(int)$idRkm,$sales,$today,true);
    if (!$plan) throw new RuntimeException('PLAN_NOT_FOUND');
    if (strtolower((string)$plan['status'])!=='aktif') throw new RuntimeException('PLAN_INACTIVE');
    $members=rkm_decode_list($plan['daftar_member']); $memberName='';
    foreach($members as $it) if (rkm_member_code($it)===$member) { $memberName=rkm_member_name($it); break; }
    if (!in_array($member,array_map('rkm_member_code',$members),true)) throw new RuntimeException('MEMBER_NOT_IN_PLAN');

    $st=$db->prepare("SELECT payload_hash,response_json FROM rkm_idempotency WHERE kode_salesman=? AND idempotency_key=? LIMIT 1");
    if (!$st) throw new RuntimeException('DB');
    $st->bind_param('ss',$sales,$key); $st->execute(); $existing=$st->get_result()->fetch_assoc(); $st->close();
    if ($existing) {
        if (!hash_equals((string)$existing['payload_hash'],$payloadHash)) throw new RuntimeException('IDEMPOTENCY_CONFLICT');
        $db->commit();
        $saved=json_decode((string)$existing['response_json'],true);
        if (is_array($saved)) rkm_json_response(200,$saved);
        rkm_json_response(200,['success'=>true,'message'=>'Submit sebelumnya sudah tersimpan.']);
    }
    /* Jangan menerima submit jika sudah ada hasil untuk member pada rencana hari ini. */
    $st=$db->prepare("SELECT id FROM rencana_rkm WHERE id_rkm=? AND kode_member=? LIMIT 1 FOR UPDATE");
    $st->bind_param('is',$idRkm,$member); $st->execute(); $already=$st->get_result()->fetch_assoc(); $st->close();
    if ($already) throw new RuntimeException('ALREADY_SUBMITTED');

    if (!move_uploaded_file($file['tmp_name'],$absolute)) throw new RuntimeException('PHOTO_SAVE');
    $stored=true;
    $coord=(string)$coordJson;
    $st=$db->prepare("INSERT INTO rencana_rkm (id_rkm,kode_salesman,kode_member,nama_member,urutan_kunjungan,waktu_kunjungan,hasil,catatan,bukti,koordinat_foto,follow_up,sumber_pencatatan,waktu_pencatatan,created_at)
                      VALUES (?,?,?,?,?,NOW(),?,?,?,?,?,'APK',NOW(),NOW())");
    if (!$st) throw new RuntimeException('DB');
    $order=null;
    foreach($members as $ix=>$it) if (rkm_member_code($it)===$member) { $entry=is_array($it)?$it:[]; $order=(int)($entry['urutan_kunjungan'] ?? $entry['urutan'] ?? ($ix+1)); break; }
    $st->bind_param('isssisssss',$idRkm,$sales,$member,$memberName,$order,$result,$note,$relative,$coord,$follow);
    if (!$st->execute()) { $st->close(); throw new RuntimeException('DB'); }
    $visitId=(int)$db->insert_id; $st->close();
    $response=['success'=>true,'message'=>'Hasil kunjungan berhasil disimpan','data'=>[
        'id'=>$visitId,'id_rkm'=>(int)$idRkm,'kode_member'=>$member,'hasil'=>$result,'waktu_kunjungan'=>$todayDt->format('Y-m-d H:i:s')]];
    $responseJson=json_encode($response,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    $st=$db->prepare("INSERT INTO rkm_idempotency (kode_salesman,idempotency_key,payload_hash,response_json,created_at) VALUES (?,?,?,?,NOW())");
    if (!$st) throw new RuntimeException('DB');
    $st->bind_param('ssss',$sales,$key,$payloadHash,$responseJson);
    if (!$st->execute()) { $st->close(); throw new RuntimeException('DB'); }
    $st->close();
    $db->commit();
    rkm_json_response(201,$response);
} catch (Throwable $e) {
    $db->rollback();
    if ($stored && is_file($absolute)) {
        /* File ini baru dibuat oleh request ini; hapus hanya jika belum direferensikan. */
        $st=$db->prepare("SELECT COUNT(*) AS n FROM rencana_rkm WHERE bukti=?");
        if ($st) {
            $st->bind_param('s',$relative); $st->execute(); $n=(int)($st->get_result()->fetch_assoc()['n']??0); $st->close();
            if ($n===0) @unlink($absolute);
        }
    }
    $msg=$e->getMessage();
    if ($msg==='PLAN_NOT_FOUND') rkm_json_response(403,['success'=>false,'message'=>'Rencana tidak ditemukan untuk salesman dan tanggal hari ini.']);
    if ($msg==='PLAN_INACTIVE') rkm_json_response(409,['success'=>false,'message'=>'Rencana tidak aktif.']);
    if ($msg==='MEMBER_NOT_IN_PLAN') rkm_json_response(403,['success'=>false,'message'=>'Member tidak termasuk dalam rencana ini.']);
    if ($msg==='ALREADY_SUBMITTED') rkm_json_response(409,['success'=>false,'message'=>'Member ini sudah memiliki catatan kunjungan pada rencana hari ini.']);
    if ($msg==='IDEMPOTENCY_CONFLICT') rkm_json_response(409,['success'=>false,'message'=>'idempotency_key sudah dipakai untuk data yang berbeda.']);
    if ($msg==='PHOTO_SAVE') rkm_json_response(500,['success'=>false,'message'=>'Foto gagal disimpan. Periksa akses folder D:/rkm/.']);
    error_log('RKM submit failed; code='.preg_replace('/[^A-Z_]/','',$msg));
    rkm_json_response(500,['success'=>false,'message'=>'Submit gagal diproses. Silakan coba kembali dengan idempotency_key yang sama.']);
}
