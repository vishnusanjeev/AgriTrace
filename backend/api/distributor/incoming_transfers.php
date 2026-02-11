<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

try {
    $st = $pdo->prepare(
        "SELECT
            t.id AS transfer_id,
            t.batch_id,
            t.status,
            t.created_at,
            b.batch_code,
            b.quantity_kg,
            c.crop_name,
            u.full_name AS farmer_name,
            be.tx_hash AS tx_hash
         FROM batch_transfers t
         JOIN batches b ON b.id = t.batch_id
         JOIN crops c ON c.id = b.crop_id
         JOIN users u ON u.id = t.from_user_id
         LEFT JOIN (
            SELECT be1.batch_id, be1.tx_hash
            FROM blockchain_events be1
            JOIN (
                SELECT batch_id, MAX(id) AS max_id
                FROM blockchain_events
                WHERE tx_hash IS NOT NULL
                GROUP BY batch_id
            ) bemax ON bemax.batch_id = be1.batch_id AND bemax.max_id = be1.id
         ) be ON be.batch_id = t.batch_id
         WHERE t.to_user_id = ?
           AND t.status = 'ASSIGNED'
         ORDER BY t.created_at DESC"
    );
    $st->execute([(int)$auth['uid']]);
    $rows = $st->fetchAll();

    $data = [];
    foreach ($rows as $r) {
        $data[] = [
            'transfer_id' => (int)$r['transfer_id'],
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'quantity_kg' => $r['quantity_kg'],
            'farmer_name' => $r['farmer_name'],
            'created_at' => $r['created_at'],
            'status' => $r['status'],
            'tx_hash' => $r['tx_hash']
        ];
    }

    json_out(['ok' => true, 'data' => $data]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load incoming transfers'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
