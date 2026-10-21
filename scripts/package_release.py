from io import BytesIO
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import re


def source_is_clean(text):
    position = 0
    quote = None
    while position < len(text):
        char = text[position]
        if quote:
            if char == "\\":
                position += 1
            elif char == quote:
                quote = None
        elif char in ('"', "'"):
            quote = char
        elif text[position:position + 2] in ("//", "/*"):
            return False
        position += 1
    return True


root = Path(__file__).resolve().parents[1]
version = re.search(
    r"^mod_version=(\d+\.\d+\.\d+)$",
    (root / "gradle.properties").read_text(),
    re.M,
).group(1)
stem = f"muhashist-1.21.4-v{version}"
jar = root / "build/libs" / f"{stem}.jar"
sources = root / "build/libs" / f"{stem}-sources.jar"

with ZipFile(sources) as archive:
    assert "net/spidiboost/muhashist/MuhaClient.java" in archive.namelist()
    assert "net/spidiboost/muhashist/updates/BatchAgent.java" in archive.namelist()

for folder in ("src", "tools"):
    for source in (root / folder).rglob("*.java"):
        text = source.read_text(encoding="utf-8")
        assert source_is_clean(text), f"Source comment: {source.relative_to(root)}"

with ZipFile(jar) as archive:
    metadata = json.loads(archive.read("fabric.mod.json"))
    assert metadata["id"] == "muhashist" and metadata["version"] == version
    assert metadata["name"] == "spidiboost.MuhaShist"
    assert metadata["authors"] == ["SpidiBoost"]
    assert metadata["license"] == "MIT" and metadata["environment"] == "client"
    assert metadata["contact"]["sources"] == "https://github.com/SpidiBoostMods/MuhaShist"
    assert metadata["icon"] in archive.namelist()
    assert "LICENSE" in archive.namelist()
    assert not any("/qa/" in entry for entry in archive.namelist())
    assert "net/spidiboost/muhashist/HistoryCounter.class" in archive.namelist()
    assert "net/spidiboost/muhashist/CheckReply.class" in archive.namelist()
    for removed in ("SharedUpdater", "ReserveUpdates", "RestartCommand", "RestartPolicy", "UpdateScreen"):
        assert f"net/spidiboost/muhashist/updates/{removed}.class" not in archive.namelist()
    assert "preLaunch" not in metadata["entrypoints"]
    assert "net/spidiboost/muhashist/mixin/IncomingMixin.class" in archive.namelist()
    assert "IncomingMixin" in json.loads(archive.read("muhashist.mixins.json"))["client"]
    for entry in archive.namelist():
        if entry.endswith(".class"):
            assert int.from_bytes(archive.read(entry)[6:8], "big") == 65
    with ZipFile(BytesIO(archive.read("muhashist-update-agent.jar"))) as agent:
        assert "net/spidiboost/muhashist/updates/BatchAgent.class" in agent.namelist()
        for entry in agent.namelist():
            if entry.endswith(".class"):
                assert int.from_bytes(agent.read(entry)[6:8], "big") == 65
    assert archive.read("muhashist-update-agent.jar") == archive.read("muhashist-shared-update-agent.jar")

sha = hashlib.sha256(jar.read_bytes()).hexdigest()
(root / "build/libs/muhashist-update.properties").write_text(
    f"version={version}\nminecraft=1.21.4\nartifact={stem}.jar\nsha256={sha}\n",
    encoding="utf-8",
)
with ZipFile(root / "build/libs" / f"{stem}-source.zip", "w", ZIP_DEFLATED) as archive:
    for folder in ("src", "scripts", "tools", ".github", "gradle"):
        for file in sorted((root / folder).rglob("*")):
            if (file.is_file() and "__pycache__" not in file.parts
                    and file.suffix not in (".log", ".pyc")):
                archive.write(file, f"MuhaShist/{file.relative_to(root)}")
    for name in (
        "gradlew", "gradlew.bat", "gradle.properties", "build.gradle", "updater.gradle",
        "settings.gradle", "LICENSE", "README.md", "CHANGELOG.md", "AGENTS.md", ".gitignore", ".gitattributes",
    ):
        archive.write(root / name, f"MuhaShist/{name}")

print(jar.name, sha)
