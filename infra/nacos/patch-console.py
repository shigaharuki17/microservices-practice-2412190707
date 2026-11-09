#!/usr/bin/env python3
"""Backport Nacos #15610 to 3.2.4's bundled next console, without changing server logic."""
from pathlib import Path
from io import BytesIO
import os
import sys
import zipfile

LIB = 'BOOT-INF/lib/nacos-console-3.2.4.jar'
ASSET = 'static/next/js/serviceManagement.js'
OLD = b'children:t.triggerFlag?'
NEW = b'children:(t.triggerFlag===true||t.triggerFlag===`true`)?'


def rewrite_zip(data, target, transform):
    output = BytesIO()
    with zipfile.ZipFile(BytesIO(data)) as source, zipfile.ZipFile(output, 'w') as dest:
        if source.namelist().count(target) != 1:
            raise RuntimeError('Expected exactly one resource: ' + target)
        for entry in source.infolist():
            payload = source.read(entry.filename)
            if entry.filename == target:
                payload = transform(payload)
            # Preserve entry metadata and original compression, especially STORED nested JARs.
            dest.writestr(entry, payload)
    return output.getvalue()


def fix_js(data):
    if data.count(NEW) == 1 and OLD not in data:
        return data
    if data.count(OLD) != 1:
        raise RuntimeError('Unexpected console bundle; refusing a broad or ambiguous patch')
    return data.replace(OLD, NEW, 1)


def patch(path):
    path = Path(path)
    original = path.read_bytes()
    result = rewrite_zip(original, LIB, lambda jar: rewrite_zip(jar, ASSET, fix_js))
    # Verify the completed archive before atomically replacing the original.
    with zipfile.ZipFile(BytesIO(result)) as outer:
        if outer.testzip() is not None:
            raise RuntimeError('Outer JAR integrity failed')
        with zipfile.ZipFile(BytesIO(outer.read(LIB))) as inner:
            if inner.testzip() is not None or inner.read(ASSET).count(NEW) != 1:
                raise RuntimeError('Console JAR integrity failed')
    staged = path.with_name(path.name + '.console-fix')
    staged.write_bytes(result)
    staged.chmod(path.stat().st_mode)
    os.replace(staged, path)
    print('PASS: Nacos 3.2.4 console triggerFlag parser patched; server/configuration unchanged')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: patch-console.py /home/nacos/target/nacos-server.jar')
    patch(sys.argv[1])
