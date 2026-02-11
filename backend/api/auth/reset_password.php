<?php
require_once __DIR__ . '/_bootstrap.php';

$in = json_in();
$emailRaw = trim((string)($in['email'] ?? ''));
$otp      = trim((string)($in['otp'] ?? ''));
$newPass  = (string)($in['new_password'] ?? '');

if ($emailRaw === '' || $otp === '' || $newPass === '') {
    json_out(['ok'=>false,'error'=>'Email, OTP and new password required'], 400);
}
if (strlen($newPass) < 6) json_out(['ok'=>false,'error'=>'Password must be at least 6 characters'], 400);

$email = norm_email($emailRaw);
$pdo = pdo();

$st = $pdo->prepare("SELECT id, token_version, role FROM users WHERE email_norm=? LIMIT 1");
$st->execute([$email]);
$u = $st->fetch();
if (!$u) json_out(['ok'=>false,'error'=>'User not found'], 404);

$uid = (int)$u['id'];
$purpose = 'RESET_PASSWORD';

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
    $pdo->prepare("UPDATE auth_otps SET attempts=attempts+1 WHERE id=?")->execute([(int)$row['id']]);
    json_out(['ok'=>false,'error'=>'That code is invalid.'], 400);
}

// consume otp
$pdo->prepare("UPDATE auth_otps SET consumed_at=? WHERE id=?")->execute([now(), (int)$row['id']]);

// update password + bump token_version (logout-all)
$newHash = password_hash($newPass, PASSWORD_BCRYPT);

$pdo->prepare("UPDATE users SET password_hash=?, token_version=token_version+1, failed_login_attempts=0, locked_until=NULL WHERE id=?")
    ->execute([$newHash, $uid]);

audit($pdo, $uid, 'AUTH_RESET_DONE', []);

json_out(['ok'=>true, 'message'=>'Password updated']);
