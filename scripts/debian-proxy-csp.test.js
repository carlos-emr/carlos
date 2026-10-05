// Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later.
'use strict';
const test = require('node:test');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

// carlos-ctl is its own repository since #4001: CARLOS_CTL_SRC names a
// checkout, else the installed package under /usr/lib/carlos-ctl is read.
const ctlSrc = [process.env.CARLOS_CTL_SRC, '/usr/lib/carlos-ctl']
  .find((dir) => dir && fs.existsSync(path.join(dir, 'carlos_ctl', 'config.py')));

test('nginx CSP upgrade handles stock, repeated, fresh and customized installations', { skip: !ctlSrc && 'set CARLOS_CTL_SRC to a carlos-ctl checkout' }, () => {
  const root = path.resolve(__dirname, '..');
  execFileSync('python3', ['-c', `
from pathlib import Path
import tempfile
from unittest.mock import patch
from carlos_ctl.config import _install_proxy_params
legacy = Path('scripts/fixtures/nginx-proxy-params-legacy.conf').read_bytes()
new = Path('debian/assets/nginx/proxy-params.conf').resolve()
with tempfile.TemporaryDirectory() as directory:
    target = Path(directory) / 'proxy.conf'
    target.write_bytes(legacy)
    _install_proxy_params(str(target), str(new))
    assert target.read_bytes() == new.read_bytes(), 'stock upgrade did not install CSP fix'
    _install_proxy_params(str(target), str(new))
    assert target.read_bytes() == new.read_bytes(), 'upgrade was not idempotent'
    custom = legacy + b'\\n# operator customization\\n'
    target.write_bytes(custom)
    with patch('carlos_ctl.config.warn') as warning:
        _install_proxy_params(str(target), str(new))
        assert target.read_bytes() == custom, 'operator changes were overwritten'
        warning.assert_called_once()
        assert 'Content-Security-Policy' in warning.call_args.args[0]
    target.unlink()
    _install_proxy_params(str(target), str(new))
    assert target.read_bytes() == new.read_bytes(), 'fresh install missed template'
`], { cwd: root, env: { ...process.env, PYTHONPATH: ctlSrc }, timeout: 10000 });
});
