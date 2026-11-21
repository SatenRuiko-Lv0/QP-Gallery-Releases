"""Edit application identity attributes in the pinned APK's compiled XML."""

from struct import pack, pack_into, unpack_from

UPSTREAM_PACKAGE = "com.alensw.PicFolder"
COMIC_PACKAGE = "com.qpgallery.comic"
APP_LABEL = "QP 漫画版"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
AUTHORITIES = {
    f"{UPSTREAM_PACKAGE}.{suffix}": f"{COMIC_PACKAGE}.{suffix}"
    for suffix in ("provider", "FileProvider")
}

CHUNK_HEADER = "<HHI"
STRING_POOL = 0x0001
XML = 0x0003
START_ELEMENT = 0x0102
UTF8_FLAG = 0x100
SORTED_FLAG = 0x1
NO_STRING = 0xFFFFFFFF
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10


def read_utf8_length(data, position):
    first = data[position]
    if first & 0x80:
        return ((first & 0x7F) << 8) | data[position + 1], position + 2
    return first, position + 1


class StringPool:
    def __init__(self, chunk):
        kind, header_size, size = unpack_from(CHUNK_HEADER, chunk)
        count, style_count, self.flags, strings_start, styles_start = unpack_from("<5I", chunk, 8)
        if (kind, header_size, size, style_count, styles_start, strings_start) != (
                STRING_POOL, 28, len(chunk), 0, 0, 28 + 4 * count):
            raise ValueError("Unexpected binary XML string pool")
        self.offsets = list(unpack_from(f"<{count}I", chunk, header_size))
        self.data = bytearray(chunk[strings_start:])

    def get(self, index):
        if index == NO_STRING:
            return None
        start = self.offsets[index]
        if self.flags & UTF8_FLAG:
            _, position = read_utf8_length(self.data, start)
            length, position = read_utf8_length(self.data, position)
            return self.data[position:position + length].decode("utf-8")
        length, = unpack_from("<H", self.data, start)
        if length & 0x8000:
            lower, = unpack_from("<H", self.data, start + 2)
            length = ((length & 0x7FFF) << 16) | lower
            start += 2
        return self.data[start + 2:start + 2 + 2 * length].decode("utf-16le")

    def append(self, value):
        utf16 = value.encode("utf-16le")
        length = len(utf16) // 2
        if self.flags & UTF8_FLAG:
            utf8 = value.encode("utf-8")
            if len(utf8) >= 128 or length >= 128:
                raise ValueError("Replacement XML string exceeds short UTF-8 encoding")
            encoded = bytes((length, len(utf8))) + utf8 + b"\0"
        else:
            encoded = pack("<H", length) + utf16 + b"\0\0"
        index = len(self.offsets)
        self.offsets.append(len(self.data))
        self.data.extend(encoded)
        return index

    def encode(self):
        offsets = pack(f"<{len(self.offsets)}I", *self.offsets)
        strings_start = 28 + len(offsets)
        padding = b"\0" * (-len(self.data) % 4)
        size = strings_start + len(self.data) + len(padding)
        return (pack("<HH6I", STRING_POOL, 28, size, len(self.offsets), 0,
                     self.flags & ~SORTED_FLAG, strings_start, 0)
                + offsets + self.data + padding)


def manifest_replacements(tag, attrs):
    android_name = attrs.get((ANDROID_NAMESPACE, "name"))
    if tag == "manifest" and attrs.get((None, "package")) == UPSTREAM_PACKAGE:
        return {
            (None, "package"): COMIC_PACKAGE,
            (ANDROID_NAMESPACE, "versionCode"): 9900008,
            (ANDROID_NAMESPACE, "versionName"): "10.0.2-comic.9",
        }
    if tag == "application" or (tag == "activity"
                                and android_name == f"{UPSTREAM_PACKAGE}.GalleryActivity"):
        return {(ANDROID_NAMESPACE, "label"): APP_LABEL}
    if tag == "provider":
        key = (ANDROID_NAMESPACE, "authorities")
        return {key: AUTHORITIES[attrs[key]]} if attrs.get(key) in AUTHORITIES else {}
    return {}


def shortcut_replacements(tag, attrs):
    key = (ANDROID_NAMESPACE, "targetPackage")
    if tag == "intent" and attrs.get(key) == UPSTREAM_PACKAGE:
        return {key: COMIC_PACKAGE}
    return {}


def rewrite_xml(data, replacements, expected_count):
    kind, header_size, size = unpack_from(CHUNK_HEADER, data)
    if (kind, header_size, size) != (XML, 8, len(data)):
        raise ValueError("Unexpected binary XML header")
    _, _, pool_size = unpack_from(CHUNK_HEADER, data, header_size)
    pool_end = header_size + pool_size
    pool = StringPool(data[header_size:pool_end])
    nodes = bytearray(data[pool_end:])
    new_strings = {}
    changed = 0
    position = 0
    while position < len(nodes):
        kind, node_header, size = unpack_from(CHUNK_HEADER, nodes, position)
        if size < node_header or size < 8 or position + size > len(nodes):
            raise ValueError("Invalid binary XML chunk size")
        if kind == START_ELEMENT:
            start = position + node_header
            _, name, offset, stride, count = unpack_from("<IIHHH", nodes, start)
            values = {}
            locations = {}
            for i in range(count):
                attribute = start + offset + i * stride
                ns, key, _, _, _, value_type, value = unpack_from("<IIIHBBI", nodes, attribute)
                key = (pool.get(ns), pool.get(key))
                values[key] = pool.get(value) if value_type == TYPE_STRING else value
                locations[key] = attribute
            for key, value in replacements(pool.get(name), values).items():
                location = locations[key] + 8
                if isinstance(value, int):
                    pack_into("<IHBBI", nodes, location, NO_STRING, 8, 0, TYPE_INT_DEC, value)
                else:
                    if value not in new_strings:
                        new_strings[value] = pool.append(value)
                    index = new_strings[value]
                    pack_into("<IHBBI", nodes, location, index, 8, 0, TYPE_STRING, index)
                changed += 1
        position += size
    if changed != expected_count:
        raise ValueError(f"Expected {expected_count} changed XML attributes, got {changed}")
    encoded_pool = pool.encode()
    return pack(CHUNK_HEADER, XML, 8, 8 + len(encoded_pool) + len(nodes)) + encoded_pool + nodes
