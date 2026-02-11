<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

try {
    $uid = (int)$auth['uid'];

    $st = $pdo->prepare(
        "SELECT
            t.id AS transfer_id,
            t.batch_id,
            t.created_at AS transfer_created_at,
            b.batch_code,
            b.quantity_kg,
            b.status AS batch_status,
            c.crop_name,
            u.full_name AS from_name,
            f.full_name AS farmer_name
         FROM batch_transfers t
         JOIN batches b ON b.id = t.batch_id
         JOIN crops c ON c.id = b.crop_id
         JOIN users u ON u.id = t.from_user_id
         JOIN users f ON f.id = b.created_by_user_id
         WHERE t.to_user_id = ?
           AND t.status = 'PICKED_UP'
           AND t.id = (
              SELECT MAX(t2.id) FROM batch_transfers t2
               WHERE t2.batch_id = t.batch_id AND t2.to_user_id = ?
           )
         ORDER BY t.created_at DESC"
    );
    $st->execute([$uid, $uid]);
    $rows = $st->fetchAll();

    $items = [];
    foreach ($rows as $r) {
        $items[] = [
            'transfer_id' => (int)$r['transfer_id'],
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'quantity_kg' => $r['quantity_kg'],
            'transfer_created_at' => $r['transfer_created_at'],
            'from_name' => $r['from_name'],
            'farmer_name' => $r['farmer_name'],
            'batch_status' => $r['batch_status']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load received inventory'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
