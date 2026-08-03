#!/bin/sh
# Writes public/env.js from the RUNTIME_API_URL container env var before
# starting the server — this is how one built image gets a correct API URl
# in every environment, since NEXT_PUBLIC_* vars are otherwise baked in at
# `next build` time. See frontend/src/lib/api/client.ts's apiBase().
set -e

node -e "
const fs = require('fs');
const apiUrl = process.env.RUNTIME_API_URL || '';
fs.writeFileSync('/app/public/env.js', 'window.__ENV__ = ' + JSON.stringify({ API_URL: apiUrl }) + ';\n');
"

exec "$@"
