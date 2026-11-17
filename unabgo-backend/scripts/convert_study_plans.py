"""
Convierte el JSON de planes de estudio recolectado de unab.edu.co (datos públicos) en el archivo que lee
el seeder: src/main/resources/seed/study_plans.json

Uso (desde la carpeta unabgo-backend):
    python scripts/convert_study_plans.py ../../tools/data/unab_planes_estudio.json

Entrada: {"programas": [{"programa", "slug", "fuente_url", "plan_url", "estructura": "semestres" | "texto",
"total_creditos", "semestres": [{"numero", "nombre", "creditos", "cursos": [{"nombre", "creditos"}]}], "texto",
opcionales "nivel" (pregrado, tecnologia, tecnico, posgrado) y "nivel_detalle" (especializacion, maestria, doctorado)}]}.
Los planes con semestres conservan los cursos; los planes de solo texto conservan el texto recortado.
Se descartan los registros sin nombre o sin enlace del plan y los slugs repetidos reciben un sufijo.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

DEST = Path(__file__).resolve().parents[1] / "src/main/resources/seed/study_plans.json"
MAX_TEXT = 6000


def clean(value) -> str:
    # NFKC deshace las ligaduras de los PDF ("ﬁ" pasa a "fi")
    return re.sub(r"[ \t]+", " ", unicodedata.normalize("NFKC", str(value or ""))).strip()


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def number(value):
    return int(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else None


# Niveles del recolector -> las mismas etiquetas que usan los programas (ProgramSyncService)
LEVELS = {"pregrado": "Profesional", "tecnologia": "Tecnología", "tecnico": "Técnico",
          "especializacion": "Especialización", "maestria": "Maestría", "doctorado": "Doctorado", "posgrado": "Posgrado"}


def level_of(record: dict) -> str | None:
    detail, level = clean(record.get("nivel_detalle")).lower(), clean(record.get("nivel")).lower()
    if detail in LEVELS:
        return LEVELS[detail]
    if level in LEVELS:
        return LEVELS[level]
    return clean(record.get("nivel")) or None  # etiqueta ya legible ("Profesional", "Programa"...)


def convert(record: dict) -> dict | None:
    name, plan_url = clean(record.get("programa")), clean(record.get("plan_url"))
    if not name or not plan_url:
        return None
    semesters = []
    for s in record.get("semestres") or []:
        courses = [{"name": clean(c.get("nombre")), "credits": number(c.get("creditos"))}
                   for c in s.get("cursos") or [] if clean(c.get("nombre"))]
        semesters.append({"number": number(s.get("numero")) or len(semesters) + 1,
                          "name": clean(s.get("nombre")) or f"Semestre {len(semesters) + 1}",
                          "credits": number(s.get("creditos")), "courses": courses})
    structured = record.get("estructura") == "semestres" and bool(semesters)
    item = {
        "slug": slugify(record.get("slug") or name),
        "program_name": name,
        "level": level_of(record),
        "structure": "semestres" if structured else "texto",
        "total_credits": number(record.get("total_creditos")),
        "plan_url": plan_url,
        "source_url": clean(record.get("fuente_url")) or plan_url,
        "semesters": semesters if structured else [],
        "text": None if structured else (unicodedata.normalize("NFKC", str(record.get("texto") or "")).strip()[:MAX_TEXT] or None),
    }
    if structured and item["total_credits"] is None:
        item["total_credits"] = sum(s["credits"] or 0 for s in semesters) or None
    return item


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    records = data.get("programas", []) if isinstance(data, dict) else data
    result, slugs = [], set()
    for record in records:
        item = convert(record)
        if item is None:
            continue
        base, n = item["slug"], 2
        while item["slug"] in slugs:
            item["slug"], n = f"{base}-{n}", n + 1
        slugs.add(item["slug"])
        result.append(item)
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    courses = sum(len(s["courses"]) for p in result for s in p["semesters"])
    print(f"{len(result)} planes, {sum(len(p['semesters']) for p in result)} semestres y {courses} cursos escritos en {DEST}")


if __name__ == "__main__":
    main()
