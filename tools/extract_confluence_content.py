from __future__ import annotations

import argparse
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


BREAK_TAGS = {
    "address",
    "article",
    "aside",
    "blockquote",
    "br",
    "caption",
    "div",
    "dd",
    "dl",
    "dt",
    "figcaption",
    "figure",
    "footer",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul",
}


def fix_mojibake(value: str) -> str:
    """Repair common UTF-8-as-Latin-1 export artefacts without touching clean text."""
    markers = ("Ã", "Â", "â€", "â€™", "â€œ", "â€“", "â€”")
    repaired = value
    for _ in range(2):
        if not any(marker in repaired for marker in markers):
            break
        try:
            candidate = repaired.encode("latin-1").decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            break
        if sum(candidate.count(marker) for marker in markers) >= sum(
            repaired.count(marker) for marker in markers
        ):
            break
        repaired = candidate
    return repaired


class TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.skip_depth = 0
        self.title_parts: list[str] = []
        self.in_title = False

    def handle_starttag(self, tag: str, attrs) -> None:
        tag = tag.lower()
        if tag in {"script", "style", "noscript", "svg"}:
            self.skip_depth += 1
        if tag == "title":
            self.in_title = True
        if not self.skip_depth and tag in BREAK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "title":
            self.in_title = False
        if tag in {"script", "style", "noscript", "svg"} and self.skip_depth:
            self.skip_depth -= 1
        if not self.skip_depth and tag in BREAK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth:
            return
        if self.in_title:
            self.title_parts.append(data)
        self.parts.append(data)

    def result(self) -> tuple[str, str]:
        text = html.unescape("".join(self.parts))
        text = re.sub(r"[\t\r\f\v ]+", " ", text)
        text = re.sub(r" *\n *", "\n", text)
        text = re.sub(r"\n{3,}", "\n\n", text).strip()
        title = re.sub(r"\s+", " ", "".join(self.title_parts)).strip()
        return title, text


def strip_markup(value: str) -> tuple[str, str]:
    parser = TextExtractor()
    try:
        parser.feed(value)
        parser.close()
    except Exception:
        value = re.sub(r"<[^>]+>", " ", value)
        return "", fix_mojibake(
            re.sub(r"\s+", " ", html.unescape(value)).strip()
        )
    title, text = parser.result()
    return fix_mojibake(title), fix_mojibake(text)


def direct_child(element: ET.Element, tag: str, name: str | None = None):
    for child in element:
        if child.tag != tag:
            continue
        if name is None or child.get("name") == name:
            return child
    return None


def element_text(element: ET.Element | None) -> str:
    if element is None:
        return ""
    return "".join(element.itertext()).strip()


def reference_id(element: ET.Element, property_name: str) -> str:
    prop = direct_child(element, "property", property_name)
    if prop is None:
        return ""
    ref = direct_child(prop, "id", "id")
    return element_text(ref)


def export_cloud_xml(source: Path, output: Path) -> None:
    pages: dict[str, dict] = {}
    bodies: dict[str, str] = {}

    for _event, elem in ET.iterparse(source, events=("end",)):
        if elem.tag != "object":
            continue
        object_class = elem.get("class", "")
        if object_class == "Page":
            page_id = element_text(direct_child(elem, "id", "id"))
            title = fix_mojibake(
                element_text(direct_child(elem, "property", "title"))
            )
            status = element_text(direct_child(elem, "property", "contentStatus"))
            pages[page_id] = {
                "id": page_id,
                "title": title,
                "status": status,
                "version": element_text(direct_child(elem, "property", "version")),
                "parent_id": reference_id(elem, "parent"),
                "space_id": reference_id(elem, "space"),
                "modified": element_text(
                    direct_child(elem, "property", "lastModificationDate")
                ),
            }
        elif object_class == "BodyContent":
            page_id = reference_id(elem, "content")
            body = element_text(direct_child(elem, "property", "body"))
            if page_id and body:
                bodies[page_id] = body
        elem.clear()

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for page_id, page in pages.items():
            if page.get("status") not in {"", "current"}:
                continue
            _embedded_title, body_text = strip_markup(bodies.get(page_id, ""))
            record = {**page, "body": body_text, "source": str(source)}
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def export_html_dir(source: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for path in sorted(source.rglob("*.html")):
            try:
                raw = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            title, body = strip_markup(raw)
            record = {
                "title": title or path.stem,
                "body": body,
                "source": str(path),
            }
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def search_jsonl(source: Path, query: str, limit: int) -> None:
    terms = [term.casefold() for term in re.findall(r"[\wÀ-ÿ-]+", query)]
    matches: list[tuple[int, dict]] = []
    with source.open("r", encoding="utf-8") as handle:
        for line in handle:
            record = json.loads(line)
            title = record.get("title", "")
            body = record.get("body", "")
            haystack = f"{title}\n{body}".casefold()
            if not all(term in haystack for term in terms):
                continue
            score = sum(title.casefold().count(term) * 5 for term in terms)
            score += sum(body.casefold().count(term) for term in terms)
            matches.append((score, record))

    for score, record in sorted(matches, key=lambda item: item[0], reverse=True)[:limit]:
        body = record.get("body", "")
        folded = body.casefold()
        positions = [folded.find(term) for term in terms if folded.find(term) >= 0]
        start = max(0, (min(positions) if positions else 0) - 350)
        excerpt = body[start : start + 1800]
        print(
            json.dumps(
                {
                    "score": score,
                    "title": record.get("title", ""),
                    "source": record.get("source", ""),
                    "excerpt": excerpt,
                },
                ensure_ascii=False,
            )
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    cloud = subparsers.add_parser("cloud-xml")
    cloud.add_argument("source", type=Path)
    cloud.add_argument("output", type=Path)

    html_dir = subparsers.add_parser("html-dir")
    html_dir.add_argument("source", type=Path)
    html_dir.add_argument("output", type=Path)

    search = subparsers.add_parser("search")
    search.add_argument("source", type=Path)
    search.add_argument("query")
    search.add_argument("--limit", type=int, default=10)

    args = parser.parse_args()
    if args.command == "cloud-xml":
        export_cloud_xml(args.source, args.output)
    elif args.command == "html-dir":
        export_html_dir(args.source, args.output)
    elif args.command == "search":
        search_jsonl(args.source, args.query, args.limit)


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        sys.exit(0)
