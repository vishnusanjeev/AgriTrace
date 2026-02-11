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
            t.created_at,
            b.batch_code,
            b.quantity_kg,
            c.crop_name,
            u.full_name AS from_name,
            (
                SELECT e.tx_hash FROM blockchain_events e
                WHERE e.batch_id = b.id AND e.event_name = 'BatchCreated'
                ORDER BY e.id DESC LIMIT 1
            ) AS tx_hash
         FROM batch_transfers t
         JOIN batches b ON b.id = t.batch_id
         JOIN crops c ON c.id = b.crop_id
         JOIN users u ON u.id = t.from_user_id
         WHERE t.to_user_id = ? AND t.status = 'ASSIGNED'
         ORDER BY t.created_at DESC"
    );
    $st->execute([$uid]);
    $rows = $st->fetchAll();

    $items = [];
    foreach ($rows as $r) {
        $items[] = [
            'transfer_id' => (int)$r['transfer_id'],
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'quantity_kg' => $r['quantity_kg'],
            'from_name' => $r['from_name'],
            'created_at' => $r['created_at'],
            'tx_hash' => $r['tx_hash']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load incoming stock'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
