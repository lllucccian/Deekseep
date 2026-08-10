#!/usr/bin/env python3
"""Rank likely R8-renamed JADX classes by stable literals and code shape."""

from __future__ import annotations

import argparse
import math
import re
from collections import Counter
from pathlib import Path


STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
RESOURCE_RE = re.compile(r"\bR\.(?:string|drawable|id|color|dimen|style)\.[A-Za-z0-9_]+")
JAVA_KEYWORDS = {
    "abstract", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends",
    "false", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new", "null",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "true", "try", "void", "volatile", "while",
}
TOKEN_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*|\d+|==|!=|<=|>=|&&|\|\||[{}()[\];,.?:=+\-*/]")


def java_files(root: Path) -> dict[str, Path]:
    return {path.stem: path for path in root.glob("*.java")}


def literals(text: str) -> set[str]:
    found = set(RESOURCE_RE.findall(text))
    for token in STRING_RE.findall(text):
        value = token[1:-1]
        if len(value) >= 4 and not value.startswith("Method not decompiled:"):
            found.add(value)
    return found


def skeleton(text: str) -> tuple[str, int, int, int]:
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    text = STRING_RE.sub(" S ", text)
    out: list[str] = []
    for token in TOKEN_RE.findall(text):
        if token in JAVA_KEYWORDS or not token[0].isalpha():
            out.append(token)
        elif token in {"String", "Object", "List", "Map", "Set", "Context", "Uri",
                       "JSONObject", "JSONArray", "Iterator", "Collection"}:
            out.append(token)
        else:
            out.append("I")
    normalized = " ".join(out[:24000])
    return (
        normalized,
        len(text),
        len(re.findall(r"\b(?:public|private|protected)\b[^;{}]*\(", text)),
        len(re.findall(r"\b(?:public|private|protected)\b[^;{}]*;", text)),
    )


def grams(value: str, width: int = 7) -> set[tuple[str, ...]]:
    tokens = value.split()
    if len(tokens) < width:
        return {tuple(tokens)} if tokens else set()
    return {tuple(tokens[index:index + width])
            for index in range(len(tokens) - width + 1)}


def ratio_score(left: int, right: int) -> float:
    if left <= 0 or right <= 0:
        return 0.0
    return math.exp(-abs(math.log(left / right)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("old")
    parser.add_argument("new")
    parser.add_argument("classes", nargs="+")
    parser.add_argument("--limit", type=int, default=6)
    args = parser.parse_args()

    old_files = java_files(Path(args.old))
    new_files = java_files(Path(args.new))
    new_text = {name: path.read_text(errors="ignore") for name, path in new_files.items()}
    new_literals = {name: literals(text) for name, text in new_text.items()}
    frequency: Counter[str] = Counter()
    for values in new_literals.values():
        frequency.update(values)
    total = max(1, len(new_files))
    new_shape = {name: skeleton(text) for name, text in new_text.items()}
    new_grams = {name: grams(shape[0]) for name, shape in new_shape.items()}

    for old_name in args.classes:
        old_path = old_files.get(old_name)
        if old_path is None:
            print(f"{old_name}: missing")
            continue
        text = old_path.read_text(errors="ignore")
        anchors = literals(text)
        shape = skeleton(text)
        shape_grams = grams(shape[0])
        anchor_weight = sum(math.log((total + 1) / (frequency[item] + 1)) + 1
                            for item in anchors) or 1.0
        ranked: list[tuple[float, str, float, float, float]] = []
        for candidate in new_files:
            other = new_shape[candidate]
            size = ratio_score(shape[1], other[1])
            methods = ratio_score(shape[2] + 1, other[2] + 1)
            fields = ratio_score(shape[3] + 1, other[3] + 1)
            overlap = anchors & new_literals[candidate]
            anchor = sum(math.log((total + 1) / (frequency[item] + 1)) + 1
                         for item in overlap) / anchor_weight
            candidate_grams = new_grams[candidate]
            union = len(shape_grams | candidate_grams)
            code = len(shape_grams & candidate_grams) / union if union else 0.0
            score = anchor * 0.62 + code * 0.23 + size * 0.08
            score += methods * 0.045 + fields * 0.025
            ranked.append((score, candidate, anchor, code, size))
        ranked.sort(reverse=True)
        print(f"{old_name}:")
        for score, candidate, anchor, code, size in ranked[:args.limit]:
            print(f"  {candidate:12} score={score:.4f} literals={anchor:.3f} "
                  f"shape={code:.3f} size={size:.3f}")


if __name__ == "__main__":
    main()
