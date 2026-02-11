<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

try {
    $uid = (int)$auth['uid'];

    $st = $pdo->prepare(
        "SELECT
            COALESCE(SUM(CASE WHEN status NOT IN ('REJECTED','CANCELLED') THEN 1 ELSE 0 END), 0) AS handled,
            COALESCE(SUM(CASE WHEN status = 'ASSIGNED' THEN 1 ELSE 0 END), 0) AS incoming,
            COALESCE(SUM(CASE WHEN status = 'PICKED_UP' THEN 1 ELSE 0 END), 0) AS picked_up,
            COALESCE(SUM(CASE WHEN status IN ('IN_TRANSIT','PICKED_UP') THEN 1 ELSE 0 END), 0) AS inventory
         FROM batch_transfers
         WHERE to_user_id = ?"
    );
    $st->execute([$uid]);
    $row = $st->fetch();

    $st = $pdo->prepare(
        "SELECT COUNT(DISTINCT t.batch_id) AS cnt
         FROM batch_transfers t
         WHERE t.to_user_id = ?
           AND t.status = 'IN_TRANSIT'
           AND EXISTS (
              SELECT 1 FROM batch_scan_events e
               WHERE e.batch_id = t.batch_id
                 AND e.actor_user_id = ?
                 AND e.event_type = 'TRANSPORT_UPDATE'
                 AND e.result = 'OK'
                 AND e.actor_role = 'DISTRIBUTOR'
           )"
    );
    $st->execute([$uid, $uid]);
    $inTransitRow = $st->fetch();

    $stats = [
        'handled' => (int)($row['handled'] ?? 0),
        'in_transit' => (int)($inTransitRow['cnt'] ?? 0),
        'delivered' => 0,
        'incoming' => (int)($row['incoming'] ?? 0),
        'inventory' => (int)($row['inventory'] ?? 0),
        'picked_up' => (int)($row['picked_up'] ?? 0)
    ];

    json_out(['ok' => true, 'stats' => $stats]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load distributor dashboard'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
