<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['CONSUMER']);
$pdo = pdo();

try {
    $st = $pdo->prepare("SELECT s.batch_id, s.created_at, s.result,
                                b.batch_code, c.crop_name, c.category
                         FROM batch_scan_events s
                         JOIN batches b ON b.id = s.batch_id
                         JOIN crops c ON c.id = b.crop_id
                         WHERE s.actor_user_id = ? AND s.event_type = 'CONSUMER_SCAN'
                         ORDER BY s.created_at DESC
                         LIMIT 3");
    $st->execute([(int)$auth['uid']]);
    $items = [];
    while ($r = $st->fetch()) {
        $items[] = [
            'batch_id' => (int)$r['batch_id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'category' => $r['category'],
            'scanned_at' => $r['created_at'],
            'result' => $r['result']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load recent scans'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
