"""Lectura de las dos versiones que se comparan: la etiqueta v1.0.0 (git show) y el árbol de trabajo (v1.1.0)."""
import subprocess
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[4]
ANTES, DESPUES = 'v1.0.0', 'v1.1.0'
VERSIONES = (ANTES, DESPUES)


def git(*args):
    return subprocess.run(['git', '-C', str(RAIZ), *args], capture_output=True, check=True).stdout


def leer(version, ruta):
    if version == ANTES:
        return git('show', f'{ANTES}:{ruta}')
    return (RAIZ / ruta).read_bytes()


def existe(version, ruta):
    if version == ANTES:
        return subprocess.run(['git', '-C', str(RAIZ), 'cat-file', '-e', f'{ANTES}:{ruta}'], capture_output=True).returncode == 0
    return (RAIZ / ruta).is_file()


def listar(version, carpeta='ontologies'):
    if version == ANTES:
        return sorted(git('ls-tree', '-r', '--name-only', ANTES, carpeta + '/').decode().split())
    return sorted(str(p.relative_to(RAIZ)) for p in (RAIZ / carpeta).rglob('*') if p.is_file())
