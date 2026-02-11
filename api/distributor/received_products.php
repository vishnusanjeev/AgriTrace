<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

try {
    $st = $pdo->prepare(
        "SELECT
            t.batch_id,
            t.status AS transfer_status,
            t.created_at AS transfer_created_at,
            b.batch_code,
            c.crop_name,
            u.full_name AS farmer_name
         FROM batch_transfers t
         JOIN batches b ON b.id = t.batch_id
         JOIN crops c ON c.id = b.crop_id
         JOIN users u ON u.id = t.from_user_id
         WHERE t.to_user_id = ?
           AND t.status IN ('IN_TRANSIT','PICKED_UP')
         ORDER BY t.created_at DESC"
    );
    $st->execute([(int)$auth['uid']]);
    $rows = $st->fetchAll();

    $data = [];
    foreach ($rows as $r) {
        $data[] = [
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'farmer_name' => $r['farmer_name'],
            'transfer_status' => $r['transfer_status'],
            'transfer_created_at' => $r['transfer_created_at']
        ];
    }

    json_out(['ok' => true, 'data' => $data]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load received products'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
