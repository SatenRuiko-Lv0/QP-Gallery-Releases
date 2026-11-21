"""Patch the viewer, settings and application identity in the pinned Alpha 2 APK."""

import re
import sys
from pathlib import Path
from zipfile import ZipFile

from standalone_manifest import (
    COMIC_PACKAGE,
    UPSTREAM_PACKAGE,
    manifest_replacements,
    rewrite_xml,
    shortcut_replacements,
)

COMIC = "Lcom/alensw/comic/ComicMode;"
VIEW = "Landroid/view/View;"
BUNDLE = "Landroid/os/Bundle;"
FOLDER = "Lcom/alensw/bean/CommonFolder;"
FILE = "Lcom/alensw/bean/CommonFile;"
LOCAL = "Lcom/alensw/a/e;"
HOST = "Lcom/alensw/ui/c/cx;"
VIEWER = "Lcom/alensw/ui/c/cu;"
URI = "Landroid/net/Uri;"


def replace_once(source, old, new):
    if source.count(old) != 1:
        raise ValueError(f"Expected one patch anchor: {old.strip()}")
    return source.replace(old, new, 1)


def patch_method(source, signature, edit):
    pattern = rf"(?m)^\.method [^\r\n]* {re.escape(signature)}\n.*?^\.end method"
    matches = list(re.finditer(pattern, source, re.DOTALL))
    if len(matches) != 1:
        raise ValueError(f"Expected one method: {signature}")
    match = matches[0]
    return source[:match.start()] + edit(match.group()) + source[match.end():]


def before_code(body, instructions):
    locals_line = re.search(r"^    \.locals \d+$", body, re.MULTILINE)
    if locals_line is None:
        raise ValueError("Expected a .locals directive")
    end = locals_line.end()
    return body[:end] + "\n" + instructions + body[end:]


def patch_viewer(path, viewer, folder, type_field, remember):
    source = path.read_text(encoding="utf-8")
    source = replace_once(source, "# instance fields\n",
                          f"# instance fields\n.field private comicState:{BUNDLE}\n")
    source = patch_method(source, f"a({BUNDLE})V", lambda body: replace_once(
        body, f"    invoke-super {{p0, p1}}, Lcom/alensw/ui/c/a;->a({BUNDLE})V",
        f"    invoke-super {{p0, p1}}, Lcom/alensw/ui/c/a;->a({BUNDLE})V\n\n"
        f"    iput-object p1, p0, {viewer}->comicState:{BUNDLE}"))

    start_anchor = f"    invoke-direct {{p0}}, {viewer}->{'y' if folder == LOCAL else 'v'}()V"
    source = patch_method(source, "a()V", lambda body: replace_once(
        body, start_anchor,
        start_anchor + f"""

    invoke-virtual {{p0}}, {viewer}->openComicReader()Z
    move-result v0
    if-eqz v0, :comic_default_viewer
    return-void

    :comic_default_viewer"""))

    source = patch_method(source, "a(Landroid/view/MotionEvent;)Z", lambda body: before_code(
        body, f"""
    iget-object v0, p0, {viewer}->D:{VIEW}
    invoke-static {{v0}}, {COMIC}->active({VIEW})Z
    move-result v0
    if-eqz v0, :comic_default_touch
    const/4 v0, 0x0
    return v0

    :comic_default_touch
"""))

    source = patch_method(source, "b()V", lambda body: before_code(body, f"""
    invoke-direct {{p0}}, {viewer}->rememberComicPage()V
    iget-object v0, p0, {viewer}->D:{VIEW}
    invoke-static {{v0}}, {COMIC}->releaseReaderChrome({VIEW})V
"""))
    source = patch_method(source, f"b({BUNDLE})V", lambda body: before_code(body, f"""
    iget-object v0, p0, {viewer}->D:{VIEW}
    invoke-static {{v0, p1}}, {COMIC}->save({VIEW}{BUNDLE})V
    invoke-direct {{p0}}, {viewer}->rememberComicPage()V
"""))

    current = "C" if folder == LOCAL else FILE
    getter = "iget-char" if folder == LOCAL else "iget-object"
    open_method = "openLocal" if folder == LOCAL else "openFolder"
    source += f"""

.method final openComicReader()Z
    .locals 5
    iget-object v0, p0, {viewer}->C:{HOST}
    iget-object v1, p0, {viewer}->D:{VIEW}
    iget-object v2, p0, {viewer}->{'m' if folder == LOCAL else 'k'}:{folder}
    {getter} v3, p0, {viewer}->{type_field}:{current}
    iget-object v4, p0, {viewer}->comicState:{BUNDLE}
    invoke-static {{v0, v1, v2, v3, v4}}, {COMIC}->{open_method}({HOST}{VIEW}{folder}{current}{BUNDLE})Z
    move-result v1
    if-eqz v1, :comic_unavailable
    const/4 v2, 0x0
    iput-object v2, p0, {viewer}->comicState:{BUNDLE}
    :comic_unavailable
    return v1
.end method

.method private rememberComicPage()V
    .locals 2
    iget-object v0, p0, {viewer}->D:{VIEW}
    invoke-static {{v0}}, {COMIC}->selectedSourceIndex({VIEW})I
    move-result v0
    if-ltz v0, :comic_no_page
{remember}
    :comic_no_page
    return-void
.end method
"""
    path.write_text(source, encoding="utf-8", newline="\n")


def patch(decoded):
    code = decoded / "smali/com/alensw/ui/c"
    local = "Lcom/alensw/ui/c/dp;"
    document = "Lcom/alensw/ui/c/as;"
    patch_viewer(code / "dp.smali", local, LOCAL, "n", f"""\
    iget-object v1, p0, {local}->m:{LOCAL}
    iput v0, v1, {LOCAL}->c:I
    invoke-virtual {{v1, v0}}, {LOCAL}->d(I){URI}
    move-result-object v0
    iput-object v0, p0, {local}->o:{URI}""")
    patch_viewer(decoded / "smali_classes4/com/alensw/ui/c/as.smali",
                 document, FOLDER, "l", f"""\
    iget-object v1, p0, {document}->k:{FOLDER}
    iput v0, v1, {FOLDER}->k:I
    invoke-virtual {{v1, v0}}, {FOLDER}->a(I){FILE}
    move-result-object v0
    iput-object v0, p0, {document}->l:{FILE}""")

    # The local viewer also resolves albums asynchronously after its initial start.
    callback = code / "du.smali"
    anchor = f"    invoke-static {{v0}}, {local}->d({local})V"
    callback.write_text(replace_once(callback.read_text(encoding="utf-8"), anchor,
                        anchor + f"\n    invoke-virtual {{v0}}, {local}->openComicReader()Z"),
                        encoding="utf-8", newline="\n")

    settings = decoded / "smali_classes2/com/alensw/ui/activity/MainPreference.smali"
    screen = "Landroid/preference/PreferenceScreen;"
    settings.write_text(patch_method(settings.read_text(encoding="utf-8"), f"a({screen})V",
                        lambda body: replace_once(body, "    return-void",
                            f"    invoke-static {{p1}}, {COMIC}->addPreference({screen})V\n\n"
                            "    return-void")), encoding="utf-8", newline="\n")

    patch_standalone_code(decoded)
    patch_reader_chrome(decoded)


def patch_reader_chrome(decoded):
    source_dir = decoded / "smali_classes4/com/alensw"
    host = source_dir / "ui/c/cx.smali"
    source = host.read_text(encoding="utf-8")
    for method in ("onCreateOptionsMenu", "onPrepareOptionsMenu"):
        source = patch_method(source, f"{method}(Landroid/view/Menu;)Z",
                              lambda body: before_code(body, f"""
    invoke-direct {{p0}}, {HOST}->hasComicReader()Z
    move-result v0
    if-eqz v0, :normal_menu
    invoke-interface {{p1}}, Landroid/view/Menu;->clear()V
    const/4 v0, 0x1
    return v0

    :normal_menu
"""))
    source += f"""

.method private hasComicReader()Z
    .locals 1
    invoke-virtual {{p0}}, {HOST}->c(){VIEWER}
    move-result-object v0
    if-eqz v0, :comic_absent
    invoke-virtual {{v0}}, {VIEWER}->D(){VIEW}
    move-result-object v0
    invoke-static {{v0}}, {COMIC}->active({VIEW})Z
    move-result v0
    return v0

    :comic_absent
    const/4 v0, 0x0
    return v0
.end method

# The legacy callback reveals the action bar when Android makes its navigation bar
# visible. ComicReader owns that visibility while the comic viewer is current.
.method protected d(I)V
    .locals 1
    invoke-direct {{p0}}, {HOST}->hasComicReader()Z
    move-result v0
    if-nez v0, :done
    invoke-super {{p0, p1}}, Lcom/alensw/ui/activity/p;->d(I)V
    :done
    return-void
.end method

.method protected final closeComicReader()Z
    .locals 2
    invoke-virtual {{p0}}, {HOST}->c(){VIEWER}
    move-result-object v0
    if-eqz v0, :comic_absent
    invoke-virtual {{v0}}, {VIEWER}->D(){VIEW}
    move-result-object v1
    invoke-static {{v1}}, {COMIC}->resetReaderChrome({VIEW})Z
    move-result v1
    if-eqz v1, :comic_absent
    invoke-virtual {{v0}}, {VIEWER}->E()V
    const/4 v1, 0x1
    return v1

    :comic_absent
    const/4 v1, 0x0
    return v1
.end method
"""
    host.write_text(source, encoding="utf-8", newline="\n")

    gallery = source_dir / "PicFolder/GalleryActivity.smali"
    source = patch_method(gallery.read_text(encoding="utf-8"), "onBackPressed()V",
                          lambda body: before_code(body, f"""
    invoke-virtual {{p0}}, {HOST}->closeComicReader()Z
    move-result v0
    if-eqz v0, :normal_back
    return-void

    :normal_back
"""))
    source = patch_method(source, "onOptionsItemSelected(Landroid/view/MenuItem;)Z",
                          lambda body: before_code(body, f"""
    invoke-interface {{p1}}, Landroid/view/MenuItem;->getItemId()I
    move-result v0
    const v1, 0x0102002c
    if-eq v0, v1, :comic_back
    const v1, 0x7f090033
    if-ne v0, v1, :normal_option
    :comic_back
    invoke-virtual {{p0}}, {HOST}->closeComicReader()Z
    move-result v0
    if-eqz v0, :normal_option
    return v0

    :normal_option
"""))
    gallery.write_text(source, encoding="utf-8", newline="\n")


def patch_standalone_code(decoded):
    # Change data identifiers and URI authorities while retaining DEX class names.
    package_literals = {
        f"{UPSTREAM_PACKAGE}{suffix}": f"{COMIC_PACKAGE}{suffix}"
        for suffix in ("_preferences", ".provider", ".FileProvider", ".CloudProvider", ".cloud.sync")
    }
    package_literals[f"package:{UPSTREAM_PACKAGE}"] = f"package:{COMIC_PACKAGE}"
    for suffix in (".FileProvider", ".cloud.sync"):
        package_literals[f"content://{UPSTREAM_PACKAGE}{suffix}"] = f"content://{COMIC_PACKAGE}{suffix}"
    literals = "|".join(re.escape(value) for value in package_literals)
    const_string = re.compile(rf'(?m)^([ \t]*const-string(?:/jumbo)? \w+, ")({literals})"$')
    changed = 0
    for source_path in decoded.glob("smali*/**/*.smali"):
        source = source_path.read_text(encoding="utf-8")
        result, count = const_string.subn(
            lambda match: match[1] + package_literals[match[2]] + '"', source)
        if count:
            changed += count
            source_path.write_text(result, encoding="utf-8", newline="\n")
    if changed != 32:
        raise ValueError(f"Unexpected number of application identity references: {changed}")

    updater = decoded / "smali_classes3/com/alensw/updater/UpdaterAssistant.smali"
    host = "Lcom/alensw/updater/UpdaterAssistant;"
    context = "Landroid/content/Context;"
    source = patch_method(updater.read_text(encoding="utf-8"), "checkForUpdates()V", lambda _: f"""
.method public checkForUpdates()V
    .locals 2
    iget-boolean v0, p0, {host}->showToast:Z
    if-eqz v0, :done
    iget-object v1, p0, {host}->c:{context}
    invoke-static {{v1}}, {COMIC}->showStandaloneUpdateMessage({context})V
    :done
    return-void
.end method""".lstrip())
    updater.write_text(source, encoding="utf-8", newline="\n")


def package(original, rebuilt, additional_dex, output):
    changed_dex = {"classes.dex", "classes2.dex", "classes3.dex", "classes4.dex"}
    xml_patches = {
        "AndroidManifest.xml": (manifest_replacements, 7),
        "res/xml/shortcuts.xml": (shortcut_replacements, 1),
    }
    with ZipFile(original) as base, ZipFile(rebuilt) as updated, ZipFile(output, "w") as apk:
        for entry in base.infolist():
            if entry.filename.startswith("META-INF/"):
                continue
            archive = updated if entry.filename in changed_dex else base
            payload = archive.read(entry.filename)
            if entry.filename in xml_patches:
                payload = rewrite_xml(payload, *xml_patches[entry.filename])
            apk.writestr(entry, payload)
        if "classes5.dex" in base.namelist():
            raise ValueError("Unexpected extra DEX file in input")
        apk.write(additional_dex, "classes5.dex")


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    if command == "patch":
        patch(Path(args[0]))
    elif command == "package":
        package(*(Path(arg) for arg in args))
    else:
        raise ValueError(f"Unknown command: {command}")
