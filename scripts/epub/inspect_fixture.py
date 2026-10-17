#!/usr/bin/env python3
# KMK -->
"""Bounded EPUB ZIP/package inspection; does not render or extract book text.

Local: python3 scripts/epub/inspect_fixture.py /path/book.epub
Remote: ... --remote gdrive-hasan:path/book.epub --size <observed-byte-size>
Remote reads use authenticated rclone ranges, never public links or copied credentials.
"""
import argparse
import io
import json
import posixpath
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET
from xml.parsers import expat
import zipfile
from pathlib import Path

MAX_RANGE = 16 * 1024 * 1024
MAX_XML = 8 * 1024 * 1024


class RcloneRangeFile(io.RawIOBase):
    def __init__(self, remote, size):
        if ":" not in remote or remote.startswith("-") or size < 0:
            raise ValueError("Expected an rclone remote path and nonnegative file size")
        self.remote = remote
        self.size = size
        self.position = 0
        self.bytes_read = 0
        self.requests = 0

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.position

    def seek(self, offset, whence=io.SEEK_SET):
        base = {io.SEEK_SET: 0, io.SEEK_CUR: self.position, io.SEEK_END: self.size}.get(whence)
        if base is None or base + offset < 0:
            raise ValueError("Invalid seek")
        self.position = base + offset
        return self.position

    def read(self, size=-1):
        count = max(0, self.size - self.position)
        if size >= 0:
            count = min(count, size)
        if count > MAX_RANGE:
            raise ValueError("Requested range exceeds the metadata inspection limit")
        if count == 0:
            return b""
        result = subprocess.run(
            ["rclone", "cat", self.remote, "--offset", str(self.position), "--count", str(count)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120, check=False,
        )
        if result.returncode:
            raise OSError("Authenticated rclone range read failed; no publication data was printed")
        if len(result.stdout) != count:
            raise OSError("Incomplete range: check file size or whether the remote file changed")
        self.position += count
        self.bytes_read += count
        self.requests += 1
        return result.stdout


def unsafe_path(name):
    normalized = name.replace("\\", "/")
    return (
        normalized.startswith("/")
        or ".." in normalized.split("/")
        or ":" in normalized.split("/")[0]
    )


def resolve_resource(base, href):
    url = urllib.parse.urlsplit(href)
    if url.scheme or url.netloc:
        return None
    path = urllib.parse.unquote(url.path)
    resolved = posixpath.normpath(posixpath.join(base, path))
    if unsafe_path(resolved):
        raise ValueError("Publication resource escapes archive root")
    return resolved


def xml_entry(archive, name, max_bytes=MAX_XML):
    info = archive.getinfo(name)
    if info.file_size > max_bytes:
        raise ValueError(f"XML metadata is {info.file_size} bytes, exceeding limit {max_bytes}")
    with archive.open(info) as stream:
        data = stream.read(max_bytes + 1)
    if len(data) > max_bytes:
        raise ValueError("XML metadata exceeds inspection limit")
    def reject_declaration(*_):
        raise ValueError("DTD/entity metadata is not accepted by this inspection harness")

    # Expat recognizes XML encodings before invoking handlers (including UTF-16).
    # Reject declarations before an entity can expand, not with a byte-pattern check.
    parser = expat.ParserCreate()
    parser.StartDoctypeDeclHandler = reject_declaration
    parser.EntityDeclHandler = reject_declaration
    parser.ExternalEntityRefHandler = reject_declaration
    parser.Parse(data, True)
    return ET.fromstring(data)


def estimate_positions(spine_items, items, files, base, page_bytes):
    """Model Readium's reflowable ArchiveEntryLength without opening chapter resources."""
    count = 0
    for itemref in spine_items:
        item = items.get(itemref.get("idref"))
        if item is None:
            continue
        path = resolve_resource(base, item.get("href", ""))
        entry = files.get(path)
        if entry is not None:
            length = entry.file_size if entry.compress_type == zipfile.ZIP_STORED else entry.compress_size
            count += max(1, (length + page_bytes - 1) // page_bytes)
    return count


def inspect(source, max_xml=MAX_XML):
    if not 0 < max_xml <= 64 * 1024 * 1024:
        raise ValueError("XML inspection limit must be between 1 byte and 64 MiB")
    with zipfile.ZipFile(source) as archive:
        infos = archive.infolist()
        files = {entry.filename: entry for entry in infos if not entry.is_dir()}
        container = xml_entry(archive, "META-INF/container.xml")
        rootfiles = container.findall(".//{*}rootfile")
        package_path = next((item.get("full-path") for item in rootfiles if item.get("full-path")), None)
        if not package_path or unsafe_path(package_path):
            raise ValueError("Missing or unsafe EPUB package path")
        package = xml_entry(archive, package_path, max_xml)
        base = posixpath.dirname(package_path)
        manifest = package.findall("./{*}manifest/{*}item")
        items = {item.get("id"): item for item in manifest if item.get("id")}
        spine = package.find("./{*}spine")
        spine_items = [] if spine is None else list(spine)
        missing = 0
        external = 0
        text_sizes = []
        image_sizes = []
        font_count = 0
        for item in manifest:
            path = resolve_resource(base, item.get("href", ""))
            if path is None:
                external += 1
                continue
            entry = files.get(path)
            if entry is None:
                missing += 1
                continue
            media_type = item.get("media-type", "")
            if media_type in ("application/xhtml+xml", "text/html"):
                text_sizes.append(entry.file_size)
            elif media_type.startswith("image/"):
                image_sizes.append(entry.file_size)
            if "font" in media_type or path.lower().endswith((".ttf", ".otf", ".woff", ".woff2")):
                font_count += 1
        layout = next((
            (item.text or "").strip()
            for item in package.findall("./{*}metadata/{*}meta")
            if item.get("property") == "rendition:layout"
        ), "not-declared")
        position_estimates = {
            str(page_bytes): estimate_positions(spine_items, items, files, base, page_bytes)
            for page_bytes in (1024, 16384)
        }
        return {
            "estimatedPositionsByArchivePageBytes": position_estimates,
            "positionEstimateScope": "Reflowable local ZIP metadata model; not rendered pages, excludes missing/external resources",
            "entryCount": len(infos),
            "packageXmlBytes": archive.getinfo(package_path).file_size,
            "xmlInspectionLimitBytes": max_xml,
            "uncompressedBytes": sum(entry.file_size for entry in infos),
            "unsafeEntryCount": sum(unsafe_path(entry.filename) for entry in infos),
            "manifestItemCount": len(manifest),
            "spineSectionCount": len(spine_items),
            "missingSpineIds": sum(item.get("idref") not in items for item in spine_items),
            "missingManifestFiles": missing,
            "externalManifestResources": external,
            "largestTextSectionBytes": max(text_sizes, default=0),
            "imageCount": len(image_sizes),
            "largestImageBytes": max(image_sizes, default=0),
            "embeddedFontCount": font_count,
            "declaredLayout": layout,
            "spineProgression": None if spine is None else spine.get("page-progression-direction"),
            "hasEncryptionMetadata": "META-INF/encryption.xml" in files,
            "zipEncryptedEntryCount": sum(bool(entry.flag_bits & 1) for entry in infos),
            "scope": "ZIP/package metadata only; renderer, visual layout and DRM usability NOT tested",
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", nargs="?")
    parser.add_argument("--remote")
    parser.add_argument("--size", type=int)
    parser.add_argument("--max-xml-bytes", type=int, default=MAX_XML, help="Explicit metadata cap, at most 64 MiB")
    args = parser.parse_args()
    if bool(args.file) == bool(args.remote) or (args.remote and args.size is None):
        parser.error("Choose one local file or --remote with --size")
    start = time.monotonic()
    if args.remote:
        source = RcloneRangeFile(args.remote, args.size)
        result = inspect(source, args.max_xml_bytes)
        result.update(fileBytes=args.size, rangeBytesRead=source.bytes_read, rangeRequests=source.requests)
    else:
        result = inspect(args.file, args.max_xml_bytes)
        result["fileBytes"] = Path(args.file).stat().st_size
    result["inspectionSeconds"] = round(time.monotonic() - start, 3)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
# KMK <--
