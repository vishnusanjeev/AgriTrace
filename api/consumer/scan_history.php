<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['CONSUMER']);
$pdo = pdo();

$q = trim((string)($_GET['q'] ?? ''));
$like = '%' . $q . '%';

try {
    $sql = "SELECT s.batch_id, s.created_at, s.result,
                   b.batch_code, c.crop_name, c.category
            FROM batch_scan_events s
            JOIN (
                SELECT batch_id, MAX(created_at) AS max_created
                FROM batch_scan_events
                WHERE actor_user_id = ? AND event_type = 'CONSUMER_SCAN'
                GROUP BY batch_id
            ) latest ON latest.batch_id = s.batch_id AND latest.max_created = s.created_at
            JOIN batches b ON b.id = s.batch_id
            JOIN crops c ON c.id = b.crop_id
            WHERE s.actor_user_id = ? AND s.event_type = 'CONSUMER_SCAN'";

    $params = [(int)$auth['uid'], (int)$auth['uid']];

    if ($q !== '') {
        $sql .= " AND (b.batch_code LIKE ? OR c.crop_name LIKE ?)";
        $params[] = $like;
        $params[] = $like;
    }

    $sql .= " ORDER BY s.created_at DESC";

    $st = $pdo->prepare($sql);
    $st->execute($params);

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
    $resp = ['ok' => false, 'error' => 'Failed to load scan history'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
