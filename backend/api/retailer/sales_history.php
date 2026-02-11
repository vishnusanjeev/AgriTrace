<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

try {
    // Optional columns (schema-safe)
    $st = $pdo->prepare("
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = ?
          AND table_name = 'batches'
          AND column_name IN ('status','sold_at','sold_price','updated_at','created_at')
    ");
    $st->execute([DB_NAME]);
    $cols = $st->fetchAll(PDO::FETCH_COLUMN);

    $hasStatus   = in_array('status', $cols, true);
    $hasSoldAt   = in_array('sold_at', $cols, true);
    $hasSoldPrice= in_array('sold_price', $cols, true);
    $hasUpdated  = in_array('updated_at', $cols, true);

    $statusExpr = $hasStatus ? "b.status" : "NULL";
    $soldAtExpr = $hasSoldAt ? "b.sold_at" : ($hasUpdated ? "b.updated_at" : "b.created_at");
    $priceExpr  = $hasSoldPrice ? "b.sold_price" : "NULL";

    /**
     * We define "belongs to retailer" as:
     *  - batch.current_owner_user_id = retailer_uid (if you have such column), OR
     *  - the latest transfer record points to this retailer (most common pattern)
     *
     * Here we implement "latest transfer to retailer" pattern safely.
     */

    $sql = "
        SELECT
            b.id AS batch_id,
            b.batch_code,
            c.crop_name,
            $statusExpr AS status,
            $priceExpr AS price,
            $soldAtExpr AS sold_at,
            b.created_at
        FROM batches b
        JOIN crops c ON c.id = b.crop_id
        WHERE EXISTS (
            SELECT 1
            FROM batch_transfers t
            WHERE t.batch_id = b.id
              AND t.to_user_id = ?
        )
        ORDER BY b.id DESC
        LIMIT 300
    ";

    $st = $pdo->prepare($sql);
    $st->execute([(int)$auth['uid']]);

    $items = [];
    while ($r = $st->fetch(PDO::FETCH_ASSOC)) {
        $items[] = [
            'batch_id'   => (int)($r['batch_id'] ?? 0),
            'batch_code' => $r['batch_code'] ?? '',
            'crop_name'  => $r['crop_name'] ?? '',
            'status'     => $r['status'],                 // AVAILABLE/SOLD/etc (or null)
            'price'      => $r['price'] !== null ? (string)$r['price'] : null,
            'sold_at'    => $r['sold_at'] ?? null,
            'created_at' => $r['created_at'] ?? null,
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load retailer history'];
    if (envv('DEV_MODE', '0') === '1') $resp['details'] = $e->getMessage();
    json_out($resp, 500);
}
