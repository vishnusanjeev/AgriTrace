<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth();
$pdo = pdo();

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

function public_url(string $relative): string {
    if ($relative === '') return '';
    if (str_starts_with($relative, 'http://') || str_starts_with($relative, 'https://')) {
        return $relative;
    }
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $scriptDir = dirname($_SERVER['SCRIPT_NAME'] ?? '/');
    $base = rtrim(dirname($scriptDir), '/') . '/';
    return $scheme . '://' . $host . $base . ltrim($relative, '/');
}

if ($method === 'GET') {
    $st = $pdo->prepare("SELECT id, full_name, email, phone_e164, role, location, profile_image_url FROM users WHERE id=? LIMIT 1");
    $st->execute([(int)$auth['uid']]);
    $u = $st->fetch();
    if (!$u) json_out(['ok' => false, 'error' => 'User not found'], 404);

    json_out([
        'ok' => true,
        'user' => [
            'id' => (int)$u['id'],
            'full_name' => $u['full_name'],
            'email' => $u['email'],
            'phone_e164' => $u['phone_e164'],
            'role' => $u['role'],
            'location' => $u['location'],
            'profile_image_url' => public_url((string)($u['profile_image_url'] ?? ''))
        ]
    ]);
}

if ($method !== 'POST') {
    json_out(['ok' => false, 'error' => 'Method not allowed'], 405);
}

$in = json_in();
$fullName = trim((string)($in['full_name'] ?? ''));
$emailRaw = trim((string)($in['email'] ?? ''));
$phoneRaw = trim((string)($in['phone_e164'] ?? ''));
$location = trim((string)($in['location'] ?? ''));
$hasProfileImage = array_key_exists('profile_image_url', $in);
$profileImageUrl = $hasProfileImage ? trim((string)($in['profile_image_url'] ?? '')) : null;

if ($fullName === '') json_out(['ok' => false, 'error' => 'Full name required'], 400);
if ($emailRaw === '' && $phoneRaw === '') {
    json_out(['ok' => false, 'error' => 'Email or phone required'], 400);
}

$email = $emailRaw !== '' ? norm_email($emailRaw) : null;
$phone = $phoneRaw !== '' ? $phoneRaw : null;

try {
    if ($email) {
        $st = $pdo->prepare("SELECT id FROM users WHERE email_norm = ? AND id <> ? LIMIT 1");
        $st->execute([$email, (int)$auth['uid']]);
        if ($st->fetch()) json_out(['ok' => false, 'error' => 'Email already in use'], 409);
    }

    if ($phone) {
        $st = $pdo->prepare("SELECT id FROM users WHERE phone_e164 = ? AND id <> ? LIMIT 1");
        $st->execute([$phone, (int)$auth['uid']]);
        if ($st->fetch()) json_out(['ok' => false, 'error' => 'Phone already in use'], 409);
    }

    if ($hasProfileImage) {
        $st = $pdo->prepare("UPDATE users SET full_name=?, email=?, phone_e164=?, location=?, profile_image_url=? WHERE id=?");
        $st->execute([
            $fullName,
            $email,
            $phone,
            $location,
            $profileImageUrl !== '' ? $profileImageUrl : null,
            (int)$auth['uid']
        ]);
    } else {
        $st = $pdo->prepare("UPDATE users SET full_name=?, email=?, phone_e164=?, location=? WHERE id=?");
        $st->execute([
            $fullName,
            $email,
            $phone,
            $location,
            (int)$auth['uid']
        ]);
    }

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
            'profile_image_url' => public_url((string)($u['profile_image_url'] ?? ''))
        ]
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to update profile'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
