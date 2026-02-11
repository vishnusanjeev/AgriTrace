<?php
// api/auth/ping.php  (quick test)
require_once __DIR__ . '/_bootstrap.php';
json_out(['ok' => true, 'ts' => now()], 200);
