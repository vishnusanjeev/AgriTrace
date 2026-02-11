<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

try {
    $st = $pdo->prepare(
        "SELECT
            t.id AS transfer_id,
            t.status AS transfer_status,
            t.created_at AS transfer_created_at,
            b.id AS batch_id,
            b.batch_code,
            b.quantity_kg,
            b.created_at AS batch_created_at,
            c.crop_name
         FROM batch_transfers t
         JOIN batches b ON b.id = t.batch_id
         JOIN crops c ON c.id = b.crop_id
         WHERE t.to_user_id = ?
           AND t.status IN ('IN_TRANSIT','PICKED_UP')
         ORDER BY t.created_at DESC"
    );
    $st->execute([(int)$auth['uid']]);
    $rows = $st->fetchAll();

    $items = [];
    foreach ($rows as $r) {
        $items[] = [
            'transfer_id' => (int)$r['transfer_id'],
            'transfer_status' => $r['transfer_status'],
            'transfer_created_at' => $r['transfer_created_at'],
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'quantity_kg' => $r['quantity_kg'],
            'batch_created_at' => $r['batch_created_at'],
            'crop_name' => $r['crop_name']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load batches'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
