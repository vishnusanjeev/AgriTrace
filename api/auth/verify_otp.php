<?php
require_once __DIR__ . '/_bootstrap.php';

$in = json_in();
$emailRaw = trim((string)($in['email'] ?? ''));
$purpose  = strtoupper(trim((string)($in['purpose'] ?? 'VERIFY_EMAIL')));
$otp      = trim((string)($in['otp'] ?? ''));

if ($emailRaw === '' || $otp === '' || $purpose === '') {
    json_out(['ok'=>false,'error'=>'Please provide email, OTP, and purpose.'], 400);
}

$email = norm_email($emailRaw);

if (!in_array($purpose, ['VERIFY_EMAIL','RESET_PASSWORD'], true)) {
    json_out(['ok'=>false,'error'=>'Invalid purpose.'], 400);
}

$pdo = pdo();

if ($purpose === 'VERIFY_EMAIL') {
    // Try pending registration first (user not created yet)
    $st = $pdo->prepare("SELECT * FROM pending_registrations WHERE email_norm=? LIMIT 1");
    $st->execute([$email]);
    $pending = $st->fetch();
    if ($pending) {
        if (strtotime((string)$pending['otp_expires_at']) <= time()) {
            json_out(['ok'=>false,'error'=>'Invalid or expired OTP'], 400);
        }

        if ((int)$pending['otp_attempts'] >= (int)$pending['otp_max_attempts']) {
            json_out(['ok'=>false,'error'=>'Too many attempts. Please request a new code.'], 429);
        }

        $hash = sha256_hex($otp);
        if (!hash_equals((string)$pending['otp_hash'], $hash)) {
            $upd = $pdo->prepare("UPDATE pending_registrations SET otp_attempts=otp_attempts+1 WHERE id=?");
            $upd->execute([(int)$pending['id']]);
            json_out(['ok'=>false,'error'=>'That code is invalid.'], 400);
        }

        try {
            $pdo->beginTransaction();

            $st = $pdo->prepare("INSERT INTO users(full_name,email,phone_e164,password_hash,role,is_active,email_verified_at)
                                 VALUES(?,?,?,?,?,1,?)");
            $st->execute([
                $pending['full_name'],
                $pending['email'],
                $pending['phone_e164'],
                $pending['password_hash'],
                $pending['role'],
                now()
            ]);
            $uid = (int)$pdo->lastInsertId();

            $del = $pdo->prepare("DELETE FROM pending_registrations WHERE id=?");
            $del->execute([(int)$pending['id']]);

            audit($pdo, $uid, 'AUTH_VERIFY_EMAIL_DONE', []);

            $pdo->commit();

            $token = jwt_sign(['uid'=>$uid, 'role'=>$pending['role'], 'tv'=>0]);

            json_out([
                'ok' => true,
                'token' => $token,
                'user' => [
                    'id' => $uid,
                    'full_name' => (string)$pending['full_name'],
                    'email' => (string)$pending['email'],
                    'phone_e164' => $pending['phone_e164'] ? (string)$pending['phone_e164'] : null,
                    'role' => (string)$pending['role'],
                    'email_verified' => true
                ]
            ]);
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            json_out(['ok'=>false,'error'=>'Something went wrong. Please try again.'], 500);
        }
    }
}

// get user (existing accounts)
$st = $pdo->prepare("SELECT id, full_name, role, phone_e164, email, token_version, is_active, email_verified_at
                     FROM users WHERE email_norm=? LIMIT 1");
$st->execute([$email]);
$u = $st->fetch();
if (!$u) json_out(['ok'=>false,'error'=>'User not found'], 404);

$uid = (int)$u['id'];

// latest valid unused otp
$st = $pdo->prepare("
    SELECT * FROM auth_otps
    WHERE sent_to=? AND purpose=? AND channel='EMAIL'
      AND consumed_at IS NULL
      AND expires_at > NOW()
    ORDER BY id DESC
    LIMIT 1
");
$st->execute([$email, $purpose]);
$row = $st->fetch();
if (!$row) json_out(['ok'=>false,'error'=>'Invalid or expired OTP'], 400);

if ((int)$row['attempts'] >= (int)$row['max_attempts']) {
    json_out(['ok'=>false,'error'=>'Too many attempts. Please request a new code.'], 429);
}

$hash = sha256_hex($otp);

if (!hash_equals((string)$row['code_hash'], $hash)) {
    $upd = $pdo->prepare("UPDATE auth_otps SET attempts=attempts+1 WHERE id=?");
    $upd->execute([(int)$row['id']]);
    json_out(['ok'=>false,'error'=>'That code is invalid.'], 400);
}

// consume
$upd = $pdo->prepare("UPDATE auth_otps SET consumed_at=? WHERE id=?");
$upd->execute([now(), (int)$row['id']]);

if ($purpose === 'VERIFY_EMAIL') {
    // activate user
    $upd2 = $pdo->prepare("UPDATE users SET email_verified_at=?, is_active=1 WHERE id=?");
    $upd2->execute([now(), $uid]);
    audit($pdo, $uid, 'AUTH_VERIFY_EMAIL_DONE', []);
}

// issue token (fresh)
$token = jwt_sign(['uid'=>$uid, 'role'=>$u['role'], 'tv'=>(int)$u['token_version']]);

json_out([
    'ok' => true,
    'token' => $token,
    'user' => [
        'id' => $uid,
        'full_name' => (string)$u['full_name'],
        'email' => $u['email'] ? (string)$u['email'] : null,
        'phone_e164' => $u['phone_e164'] ? (string)$u['phone_e164'] : null,
        'role' => (string)$u['role'],
        'email_verified' => true
    ]
]);
