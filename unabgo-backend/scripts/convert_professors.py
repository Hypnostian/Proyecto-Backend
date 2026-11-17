"""
Convierte el JSON de profesores recolectado de APOLO (apolo.unab.edu.co, datos públicos) en el archivo
que lee el seeder: src/main/resources/seed/professors.json

Uso (desde la carpeta unabgo-backend):
    python scripts/convert_professors.py ../../tools/data/unab_profesores.json

Cada profesor queda con: slug, name, title, unit, unit_url, apolo_url, cvlac_url.
El script acepta nombres de campo en español o inglés y descarta los registros sin nombre o sin enlace de APOLO.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

ALIASES = {
    "name": ["name", "nombre", "full_name", "nombre_completo"],
    "title": ["title", "cargo", "position", "titulo"],
    "unit": ["unidades", "unit", "unidad", "organisation", "organization", "dependencia", "programa"],
    "unit_url": ["unit_url", "unidad_url", "organisation_url", "organization_url"],
    "apolo_url": ["apolo_url", "url", "profile_url", "perfil_url", "apolo"],
    "cvlac_url": ["cvlac_url", "cvlac"],
}
DEST = Path(__file__).resolve().parents[1] / "src/main/resources/seed/professors.json"


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def pick(record: dict, field: str):
    for key in ALIASES[field]:
        value = record.get(key)
        if isinstance(value, list):
            value = value[0] if value else None
        if isinstance(value, dict):
            value = value.get("name") or value.get("nombre") or value.get("url")
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    if isinstance(data, dict):
        data = next((v for v in data.values() if isinstance(v, list)), [])
    result, seen, slugs = [], set(), set()
    for record in data:
        item = {field: pick(record, field) for field in ALIASES}
        units = record.get("unidades")
        if not item["unit_url"] and isinstance(units, list) and units and isinstance(units[0], dict):
            item["unit_url"] = (units[0].get("url") or "").strip() or None
        if not item["name"] or not item["apolo_url"] or item["apolo_url"] in seen:
            continue
        seen.add(item["apolo_url"])
        base = slugify(record.get("slug") or item["name"])
        item["slug"], n = base, 2
        while item["slug"] in slugs:  # al quitar tildes dos perfiles pueden quedar con el mismo slug
            item["slug"], n = f"{base}-{n}", n + 1
        slugs.add(item["slug"])
        result.append({"slug": item["slug"], **{k: item[k] for k in ALIASES}})
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(result)} profesores escritos en {DEST}")


if __name__ == "__main__":
    main()
