import pathlib
import re

def version():
    text = pathlib.Path('app/build.gradle.kts').read_text()
    name = re.search(r'versionName\s*=.*?:\s*"([0-9]+\.[0-9]+\.[0-9]+)"', text)
    code = re.search(r'versionCode\s*=.*?:\s*(\d+)', text)
    if not name or not code or int(code[1]) < 1:
        raise ValueError('Invalid Android version metadata')
    return name[1]

if __name__ == '__main__':
    print(version())
