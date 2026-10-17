# KMK -->
"""Synthetic metadata-only EPUB tests; contains no private fixture material."""
import io
import subprocess
import unittest
import zipfile
from unittest.mock import patch

from inspect_fixture import MAX_RANGE, MAX_XML, RcloneRangeFile, inspect, resolve_resource, xml_entry


def fixture(package=None, extra=(), compression=zipfile.ZIP_DEFLATED, chapter="<html>synthetic</html>"):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w", compression) as archive:
        archive.writestr("META-INF/container.xml", '<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>')
        archive.writestr("OPS/book.opf", package or (
            '<package><metadata/><manifest>'
            '<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>'
            '<item id="cover" href="cover.jpg" media-type="image/jpeg"/>'
            '<item id="font" href="font.ttf" media-type="font/ttf"/>'
            '</manifest><spine><itemref idref="chapter"/></spine></package>'
        ))
        archive.writestr("OPS/chapter.xhtml", chapter)
        archive.writestr("OPS/cover.jpg", b"image")
        archive.writestr("OPS/font.ttf", b"font")
        for name, content in extra:
            archive.writestr(name, content)
    data.seek(0)
    return data


class FixtureInspectionTest(unittest.TestCase):
    def test_counts_resources_without_reading_book_body(self):
        result = inspect(fixture())
        self.assertEqual(result["spineSectionCount"], 1)
        self.assertEqual(result["imageCount"], 1)
        self.assertEqual(result["embeddedFontCount"], 1)
        self.assertEqual(result["missingManifestFiles"], 0)

    def test_stored_resource_position_model_matches_archive_page_lengths(self):
        result = inspect(fixture(compression=zipfile.ZIP_STORED, chapter=b"x" * 1048576))
        self.assertEqual(result["estimatedPositionsByArchivePageBytes"], {"1024": 1024, "16384": 64})

    def test_deflated_position_model_uses_compressed_not_original_size(self):
        result = inspect(fixture(chapter=b"x" * 1048576))
        self.assertLess(result["estimatedPositionsByArchivePageBytes"]["1024"], 1024)
        self.assertGreaterEqual(result["estimatedPositionsByArchivePageBytes"]["16384"], 1)

    def test_reports_missing_external_and_unsafe_entries(self):
        result = inspect(fixture(
            '<package><manifest>'
            '<item id="missing" href="missing.xhtml"/>'
            '<item id="remote" href="https://example.org/book"/>'
            '</manifest><spine><itemref idref="absent"/></spine></package>',
            [("../escape", b"")],
        ))
        self.assertEqual(result["missingSpineIds"], 1)
        self.assertEqual(result["missingManifestFiles"], 1)
        self.assertEqual(result["externalManifestResources"], 1)
        self.assertEqual(result["unsafeEntryCount"], 1)

    def test_rejects_escape_but_allows_relative_resources_within_archive(self):
        self.assertEqual(resolve_resource("OPS/text", "../image.jpg"), "OPS/image.jpg")
        self.assertEqual(resolve_resource("OPS", "a%20b.xhtml#part"), "OPS/a b.xhtml")
        for href in ("../../outside", "/absolute", "%2e%2e/%2e%2e/escape"):
            with self.subTest(href=href), self.assertRaises(ValueError):
                resolve_resource("OPS", href)

    def test_rejects_entity_metadata(self):
        with self.assertRaises(ValueError):
            inspect(fixture('<!DOCTYPE package [<!ENTITY x "data">]><package/>'))

    def test_rejects_utf16_entity_metadata(self):
        metadata = '<!DOCTYPE package [<!ENTITY x "data">]><package>&x;</package>'.encode("utf-16")
        with self.assertRaises(ValueError):
            inspect(fixture(metadata))

    def test_caps_xml_decompression(self):
        with zipfile.ZipFile(fixture(extra=[("large.xml", b"x" * (MAX_XML + 1))])) as archive:
            with self.assertRaises(ValueError):
                xml_entry(archive, "large.xml")

    def test_explicit_xml_limit_is_bounded_and_recorded(self):
        for invalid in (0, -1, 64 * 1024 * 1024 + 1):
            with self.subTest(limit=invalid), self.assertRaises(ValueError):
                inspect(fixture(), invalid)
        self.assertEqual(inspect(fixture(), 1024)["xmlInspectionLimitBytes"], 1024)
        with self.assertRaises(ValueError):
            inspect(fixture(), 1)

    def test_range_seeks_and_accounts_only_requested_bytes(self):
        with patch("inspect_fixture.subprocess.run", return_value=subprocess.CompletedProcess([], 0, b"abc", b"")) as run:
            source = RcloneRangeFile("test:book.epub", 100)
            source.seek(-3, io.SEEK_END)
            self.assertEqual(source.read(50), b"abc")
            self.assertEqual(source.tell(), 100)
            self.assertEqual(source.bytes_read, 3)
            self.assertEqual(source.read(), b"")
            self.assertIn("97", run.call_args.args[0])

    def test_range_rejects_oversized_or_truncated_reads(self):
        source = RcloneRangeFile("test:book.epub", MAX_RANGE + 1)
        with self.assertRaises(ValueError):
            source.read()
        with patch("inspect_fixture.subprocess.run", return_value=subprocess.CompletedProcess([], 0, b"", b"")):
            with self.assertRaises(OSError):
                source.read(10)

    def test_range_propagates_remote_failure_without_printing_stderr(self):
        with patch("inspect_fixture.subprocess.run", return_value=subprocess.CompletedProcess([], 1, b"", b"private detail")):
            with self.assertRaisesRegex(OSError, "^Authenticated rclone range read failed"):
                RcloneRangeFile("test:book.epub", 10).read()


if __name__ == "__main__":
    unittest.main()
# KMK <--
