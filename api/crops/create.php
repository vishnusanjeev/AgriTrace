<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER']);
$in = json_in();

$cropName = trim((string)($in['crop_name'] ?? ''));
$category = trim((string)($in['category'] ?? ''));

if ($cropName === '') json_out(['ok' => false, 'error' => 'Crop name required'], 400);
if ($category === '') json_out(['ok' => false, 'error' => 'Category required'], 400);

$pdo = pdo();

try {
    $st = $pdo->prepare("INSERT INTO crops (farmer_user_id, crop_name, category) VALUES (?,?,?)");
    $st->execute([(int)$auth['uid'], $cropName, $category]);
    $id = (int)$pdo->lastInsertId();

    json_out([
        'ok' => true,
        'crop' => [
            'id' => $id,
            'crop_name' => $cropName,
            'category' => $category
        ]
    ]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Failed to create crop'], 500);
}
