<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

try {
    $retailerId = (int)$auth['uid'];

    $st = $pdo->prepare("SELECT COUNT(*) FROM batch_transfers
                         WHERE to_user_id = ? AND status = 'ASSIGNED'");
    $st->execute([$retailerId]);
    $incoming = (int)$st->fetchColumn();

    $st = $pdo->prepare("SELECT COUNT(DISTINCT t.batch_id)
                         FROM batch_transfers t
                         WHERE t.to_user_id = ?
                           AND t.status = 'PICKED_UP'
                           AND t.id = (
                               SELECT MAX(t2.id) FROM batch_transfers t2
                               WHERE t2.batch_id = t.batch_id AND t2.to_user_id = ?
                           )");
    $st->execute([$retailerId, $retailerId]);
    $received = (int)$st->fetchColumn();

    $st = $pdo->prepare("SELECT COUNT(DISTINCT t.batch_id)
                         FROM batch_transfers t
                         JOIN batches b ON b.id = t.batch_id
                         WHERE t.to_user_id = ?
                           AND t.status = 'PICKED_UP'
                           AND b.status <> 'SOLD'
                           AND t.id = (
                               SELECT MAX(t2.id) FROM batch_transfers t2
                               WHERE t2.batch_id = t.batch_id AND t2.to_user_id = ?
                           )");
    $st->execute([$retailerId, $retailerId]);
    $available = (int)$st->fetchColumn();

    $st = $pdo->prepare("SELECT COUNT(DISTINCT t.batch_id)
                         FROM batch_transfers t
                         JOIN batches b ON b.id = t.batch_id
                         WHERE t.to_user_id = ?
                           AND t.status = 'PICKED_UP'
                           AND b.status = 'SOLD'
                           AND t.id = (
                               SELECT MAX(t2.id) FROM batch_transfers t2
                               WHERE t2.batch_id = t.batch_id AND t2.to_user_id = ?
                           )");
    $st->execute([$retailerId, $retailerId]);
    $sold = (int)$st->fetchColumn();

    $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status,
                                c.crop_name, t.created_at
                         FROM batch_transfers t
                         JOIN batches b ON b.id = t.batch_id
                         JOIN crops c ON c.id = b.crop_id
                         WHERE t.to_user_id = ?
                           AND t.status IN ('ASSIGNED','IN_TRANSIT','PICKED_UP')
                           AND t.id = (
                               SELECT MAX(t2.id) FROM batch_transfers t2
                               WHERE t2.batch_id = t.batch_id AND t2.to_user_id = ?
                           )
                         ORDER BY t.created_at DESC
                         LIMIT 3");
    $st->execute([$retailerId, $retailerId]);
    $rows = $st->fetchAll();

    $recent = [];
    foreach ($rows as $r) {
        $recent[] = [
            'batch_id' => (int)$r['id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'quantity_kg' => $r['quantity_kg'],
            'harvest_date' => $r['harvest_date'],
            'status' => $r['status'],
            'created_at' => $r['created_at']
        ];
    }

    json_out([
        'ok' => true,
        'stats' => [
            'received' => $received,
            'available' => $available,
            'sold' => $sold,
            'incoming' => $incoming
        ],
        'recent' => $recent
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load retailer dashboard'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
