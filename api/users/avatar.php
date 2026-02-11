<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth();
$pdo = pdo();

if (!isset($_FILES['avatar']) || !is_uploaded_file($_FILES['avatar']['tmp_name'])) {
    json_out(['ok' => false, 'error' => 'Avatar file required'], 400);
}

$file = $_FILES['avatar'];
if ($file['error'] !== UPLOAD_ERR_OK) {
    json_out(['ok' => false, 'error' => 'Upload failed'], 400);
}

$maxBytes = 5 * 1024 * 1024;
if ($file['size'] > $maxBytes) {
    json_out(['ok' => false, 'error' => 'File too large (max 5MB)'], 400);
}

$info = getimagesize($file['tmp_name']);
if ($info === false) {
    json_out(['ok' => false, 'error' => 'Invalid image'], 400);
}

$mime = $info['mime'] ?? '';
$ext = match ($mime) {
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
    default => ''
};
if ($ext === '') {
    json_out(['ok' => false, 'error' => 'Unsupported image type'], 400);
}

$dir = __DIR__ . '/../uploads/profile';
if (!is_dir($dir)) {
    mkdir($dir, 0755, true);
}

$filename = 'u' . (int)$auth['uid'] . '_' . time() . '.' . $ext;
$dest = $dir . '/' . $filename;
if (!move_uploaded_file($file['tmp_name'], $dest)) {
    json_out(['ok' => false, 'error' => 'Failed to save image'], 500);
}

$relative = 'uploads/profile/' . $filename;

$publicUrl = function (string $relative): string {
    if ($relative === '') return '';
    if (str_starts_with($relative, 'http://') || str_starts_with($relative, 'https://')) {
        return $relative;
    }
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $scriptDir = dirname($_SERVER['SCRIPT_NAME'] ?? '/');
    $base = rtrim(dirname($scriptDir), '/') . '/';
    return $scheme . '://' . $host . $base . ltrim($relative, '/');
};

try {
    $st = $pdo->prepare("UPDATE users SET profile_image_url=? WHERE id=?");
    $st->execute([$relative, (int)$auth['uid']]);

    $st = $pdo->prepare("SELECT id, full_name, email, phone_e164, role, location, profile_image_url FROM users WHERE id=? LIMIT 1");
    $st->execute([(int)$auth['uid']]);
    $u = $st->fetch();

    json_out([
        'ok' => true,
        'user' => [
            'id' => (int)$u['id'],
            'full_name' => $u['full_name'],
            'email' => $u['email'],
            'phone_e164' => $u['phone_e164'],
            'role' => $u['role'],
            'location' => $u['location'],
            'profile_image_url' => $publicUrl((string)($u['profile_image_url'] ?? ''))
        ]
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to update avatar'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
