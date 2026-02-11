<?php
require_once __DIR__ . '/_bootstrap.php';

$in = json_in();
$identifier = trim((string)($in['identifier'] ?? ''));
$password   = (string)($in['password'] ?? '');

if ($identifier === '') json_out(['ok'=>false,'error'=>'Please enter your email or phone number.'], 400);
if ($password === '')   json_out(['ok'=>false,'error'=>'Please enter your password.'], 400);

$isEmail = (strpos($identifier, '@') !== false);
$emailNorm = $isEmail ? norm_email($identifier) : null;

$phone = $isEmail ? null : preg_replace('/[^\d\+]/', '', $identifier);
if (!$isEmail && $phone && $phone[0] !== '+' && strlen($phone) === 10) {
    $phone = '+91' . $phone;
}

$pdo = pdo();

try {
    if ($emailNorm) {
        $st = $pdo->prepare("SELECT * FROM users WHERE email_norm=? LIMIT 1");
        $st->execute([$emailNorm]);
    } else {
        $st = $pdo->prepare("SELECT * FROM users WHERE phone_e164=? LIMIT 1");
        $st->execute([$phone]);
    }

    $u = $st->fetch();
    if (!$u) json_out(['ok'=>false,'error'=>'Incorrect email/phone or password.'], 401);

    if ((int)$u['is_active'] !== 1) {
        json_out(['ok'=>false,'error'=>'Please verify your email to activate your account.'], 403);
    }

    if (!empty($u['locked_until']) && strtotime($u['locked_until']) > time()) {
        json_out(['ok'=>false,'error'=>'Your account is temporarily locked. Please try again later.'], 423);
    }

    if (!password_verify($password, (string)$u['password_hash'])) {
        $pdo->prepare("UPDATE users SET failed_login_attempts=failed_login_attempts+1 WHERE id=?")
            ->execute([(int)$u['id']]);

        $attempts = ((int)$u['failed_login_attempts']) + 1;
        if ($attempts >= 5) {
            $lockedUntil = date('Y-m-d H:i:s', time() + 10*60);
            $pdo->prepare("UPDATE users SET locked_until=? WHERE id=?")->execute([$lockedUntil, (int)$u['id']]);
        }

        json_out(['ok'=>false,'error'=>'Incorrect email/phone or password.'], 401);
    }

    $pdo->prepare("UPDATE users SET failed_login_attempts=0, locked_until=NULL, last_login_at=NOW() WHERE id=?")
        ->execute([(int)$u['id']]);

    audit($pdo, (int)$u['id'], 'AUTH_LOGIN', ['role'=>$u['role']]);

    $token = jwt_sign(['uid'=>(int)$u['id'], 'role'=>(string)$u['role'], 'tv'=>(int)$u['token_version']]);

    json_out([
        'ok' => true,
        'token' => $token,
        'user' => [
            'id' => (int)$u['id'],
            'full_name' => (string)$u['full_name'],
            'email' => $u['email'] ? (string)$u['email'] : null,
            'phone_e164' => $u['phone_e164'] ? (string)$u['phone_e164'] : null,
            'role' => (string)$u['role'],
            'email_verified' => !empty($u['email_verified_at'])
        ]
    ]);

} catch (Throwable $e) {
    json_out(['ok'=>false,'error'=>'Something went wrong on our side. Please try again.'], 500);
}
