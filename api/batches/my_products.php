<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER']);

$q = trim((string)($_GET['q'] ?? ''));
$status = strtoupper(trim((string)($_GET['status'] ?? '')));
$limit = (int)($_GET['limit'] ?? 50);
$offset = (int)($_GET['offset'] ?? 0);

if ($limit <= 0 || $limit > 200) $limit = 50;
if ($offset < 0) $offset = 0;

$allowedStatus = ['ACTIVE','PENDING','SOLD'];
$useStatus = in_array($status, $allowedStatus, true) ? $status : '';

$pdo = pdo();

$sql = "SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status, b.created_at,
               c.crop_name, c.category,
               t.status AS transfer_status
        FROM batches b
        JOIN crops c ON c.id = b.crop_id
        LEFT JOIN batch_transfers t
          ON t.id = (
              SELECT t2.id
              FROM batch_transfers t2
              WHERE t2.batch_id = b.id
              ORDER BY t2.updated_at DESC, t2.id DESC
              LIMIT 1
          )
        WHERE b.created_by_user_id = ?";
$params = [(int)$auth['uid']];

if ($useStatus !== '') {
    // Filter by batch status (transfer status filtering handled in display logic)
    $sql .= " AND b.status = ?";
    $params[] = $useStatus;
}

if ($q !== '') {
    $sql .= " AND (b.batch_code LIKE ? OR c.crop_name LIKE ? OR c.category LIKE ?)";
    $like = '%' . $q . '%';
    $params[] = $like;
    $params[] = $like;
    $params[] = $like;
}

$sql .= " ORDER BY b.created_at DESC LIMIT ? OFFSET ?";
$params[] = $limit;
$params[] = $offset;

try {
    $st = $pdo->prepare($sql);
    $st->execute($params);
    $rows = $st->fetchAll();

    $items = [];
    foreach ($rows as $r) {
        // Compute display status: prioritize transfer status over batch status
        // Same logic as farmer/home.php - show transfer status when available (unless rejected/cancelled)
        $displayStatus = $r['status']; // default to batch status
        if (!empty($r['transfer_status'])) {
            $transferStatus = $r['transfer_status'];
            // Use transfer status if it's not REJECTED or CANCELLED
            if (!in_array($transferStatus, ['REJECTED', 'CANCELLED'], true)) {
                $displayStatus = $transferStatus; // IN_TRANSIT, PICKED_UP, ASSIGNED
            }
        }
        
        $items[] = [
            'id' => (int)$r['id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'category' => $r['category'],
            'quantity_kg' => $r['quantity_kg'],
            'harvest_date' => $r['harvest_date'],
            'status' => $displayStatus, // ✅ Now shows transfer status (IN_TRANSIT, PICKED_UP) when applicable
            'created_at' => $r['created_at']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Failed to load products'], 500);
}


