from __future__ import annotations

import argparse
import json
import re
import shutil
import zipfile
from pathlib import Path, PurePosixPath
from xml.etree import ElementTree as ET


NS = {
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "pr": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def natural_key(value: str):
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", value)]


def xml_text(data: bytes, xpath: str, namespace: dict[str, str]) -> str:
    root = ET.fromstring(data)
    parts = [node.text or "" for node in root.findall(xpath, namespace)]
    return " ".join(part.strip() for part in parts if part.strip())


def rels_map(data: bytes) -> dict[str, str]:
    root = ET.fromstring(data)
    result = {}
    for node in root.findall("pr:Relationship", NS):
        result[node.attrib.get("Id", "")] = node.attrib.get("Target", "")
    return result


def normalize_zip_path(base: str, target: str) -> str:
    combined = PurePosixPath(base).parent.joinpath(target)
    parts: list[str] = []
    for part in combined.parts:
        if part == "..":
            if parts:
                parts.pop()
        elif part != ".":
            parts.append(part)
    return "/".join(parts)


def extract_pptx(path: Path, root: Path, media_root: Path):
    with zipfile.ZipFile(path) as zf:
        names = set(zf.namelist())
        slide_names = sorted(
            (name for name in names if re.fullmatch(r"ppt/slides/slide\d+\.xml", name)),
            key=natural_key,
        )
        for slide_name in slide_names:
            slide_no = int(re.search(r"(\d+)", Path(slide_name).stem).group(1))
            text = xml_text(zf.read(slide_name), ".//a:t", NS)
            rel_path = f"ppt/slides/_rels/slide{slide_no}.xml.rels"
            relationships = rels_map(zf.read(rel_path)) if rel_path in names else {}
            notes = ""
            media = []
            slide_root = ET.fromstring(zf.read(slide_name))
            used_rel_ids = {
                value
                for node in slide_root.iter()
                for key, value in node.attrib.items()
                if key.endswith("}embed") or key.endswith("}link")
            }
            for rel_id, target in relationships.items():
                target_path = normalize_zip_path(slide_name, target)
                if "notesSlides/" in target_path and target_path in names:
                    notes = xml_text(zf.read(target_path), ".//a:t", NS)
                if rel_id in used_rel_ids and target_path.startswith("ppt/media/") and target_path in names:
                    safe_stem = re.sub(r"[^A-Za-z0-9_-]+", "-", path.stem).strip("-")[:80]
                    media_dir = media_root / safe_stem
                    media_dir.mkdir(parents=True, exist_ok=True)
                    destination = media_dir / f"slide-{slide_no:03d}-{Path(target_path).name}"
                    destination.write_bytes(zf.read(target_path))
                    media.append(str(destination))
            yield {
                "type": "pptx",
                "source": str(path),
                "relative_source": str(path.relative_to(root)),
                "slide": slide_no,
                "text": text,
                "notes": notes,
                "media": media,
            }


def extract_docx(path: Path, root: Path):
    with zipfile.ZipFile(path) as zf:
        document = zf.read("word/document.xml")
        root_xml = ET.fromstring(document)
        blocks = []
        for paragraph in root_xml.findall(".//w:p", NS):
            parts = [node.text or "" for node in paragraph.findall(".//w:t", NS)]
            value = "".join(parts).strip()
            if value:
                blocks.append(value)
        yield {
            "type": "docx",
            "source": str(path),
            "relative_source": str(path.relative_to(root)),
            "text": "\n".join(blocks),
        }


def extract_pdf(path: Path, root: Path):
    try:
        from pypdf import PdfReader
    except ImportError:
        return
    reader = PdfReader(str(path))
    for page_no, page in enumerate(reader.pages, 1):
        yield {
            "type": "pdf",
            "source": str(path),
            "relative_source": str(path.relative_to(root)),
            "page": page_no,
            "text": page.extract_text() or "",
        }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("output")
    parser.add_argument("media_output")
    args = parser.parse_args()
    source = Path(args.source)
    output = Path(args.output)
    media_output = Path(args.media_output)
    output.parent.mkdir(parents=True, exist_ok=True)
    media_output.mkdir(parents=True, exist_ok=True)
    records = []
    for path in sorted(source.rglob("*"), key=lambda p: natural_key(str(p))):
        if not path.is_file():
            continue
        suffix = path.suffix.lower()
        if suffix == ".pptx":
            records.extend(extract_pptx(path, source, media_output))
        elif suffix == ".docx":
            records.extend(extract_docx(path, source))
        elif suffix == ".pdf":
            records.extend(extract_pdf(path, source))
    with output.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    print(json.dumps({"records": len(records), "output": str(output), "media": str(media_output)}))


if __name__ == "__main__":
    main()
