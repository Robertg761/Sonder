"""Sign outside the source tree. Reads private key material only from the environment."""
import base64
import hashlib
import os
import pathlib
import subprocess
import tempfile
from version import version

EXPECTED_CERT = '371bd03c279be465481ddb8504576030a206812026367654ec68ab8de77ceb12'

def run(*args, capture=False):
    return subprocess.run(args, check=True, text=True, capture_output=capture)

name = version()
build_tools = pathlib.Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))) / 'build-tools/36.0.0'
java = pathlib.Path(os.environ['JAVA_HOME']) / 'bin'
dist = pathlib.Path('dist')
dist.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='sonder-sign-', dir=os.environ.get('RUNNER_TEMP')) as temp:
    temp = pathlib.Path(temp)
    key = temp / 'release.p12'
    key.write_bytes(base64.b64decode(os.environ['SONDER_KEYSTORE_B64'], validate=True))
    key.chmod(0o600)
    password = temp / 'password.txt'
    password.write_text(os.environ['SONDER_KEYSTORE_PASSWORD'])
    password.chmod(0o600)
    aligned = temp / 'aligned.apk'
    run(str(build_tools / 'zipalign'), '-f', '-p', '4', 'app/build/outputs/apk/release/app-release-unsigned.apk', str(aligned))
    apk = dist / f'Sonder-{name}.apk'
    run(str(java / 'java'), '-jar', str(build_tools / 'lib/apksigner.jar'), 'sign', '--ks', str(key), '--ks-key-alias', 'sonder', '--ks-pass', f'file:{password}', '--v4-signing-enabled', 'false', '--out', str(apk), str(aligned))
    result = run(str(java / 'java'), '-jar', str(build_tools / 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', str(apk), capture=True)
    if f'certificate SHA-256 digest: {EXPECTED_CERT}' not in result.stdout:
        raise ValueError('Unexpected signing certificate')
    badging = run(str(build_tools / 'aapt'), 'dump', 'badging', str(apk), capture=True).stdout
    if "name='app.sonder.audiobooks'" not in badging or f"versionName='{name}'" not in badging:
        raise ValueError('APK metadata does not match release')
    run(str(java / 'jarsigner'), '-keystore', str(key), '-storepass:file', str(password), '-signedjar', str(dist / f'Sonder-{name}.aab'), 'app/build/outputs/bundle/release/app-release.aab', 'sonder')
(dist / 'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(dist.iterdir()) if p.suffix in ['.apk', '.aab']))
print(f'Verified signed release {name}.')
