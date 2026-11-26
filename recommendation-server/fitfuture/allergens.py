"""Conservative application vocabulary, including the three Android allergy groups.

This is a matching policy, not an inference of a food's ingredients. Independent evidence is
still mandatory. Unrecognized user terms or evidence labels are unverifiable, never 'safe'.
"""
GROUPS = {
    "갑각류": {"갑각류", "새우", "게", "가재", "바닷가재", "크릴"},
    "유제품": {"유제품", "우유", "치즈", "버터", "요구르트", "요거트", "유청", "카제인"},
    "견과류": {"견과류", "땅콩", "호두", "잣", "아몬드", "캐슈넛", "피스타치오", "헤이즐넛"},
}
SINGLE = {"달걀", "계란", "난류", "밀", "대두", "콩", "메밀", "고등어", "복숭아", "토마토",
          "아황산류", "돼지고기", "닭고기", "쇠고기", "소고기", "오징어", "조개류", "홍합", "굴", "전복"}


def terms(value):
    value = value.strip().casefold()
    for group, labels in GROUPS.items():
        if value in labels:
            return labels
    if value in {"달걀", "계란", "난류"}:
        return {"달걀", "계란", "난류"}
    if value in {"쇠고기", "소고기"}:
        return {"쇠고기", "소고기"}
    if value in {"대두", "콩"}:
        return {"대두", "콩"}
    if value in {"조개류", "홍합", "굴", "전복"}:
        return {"조개류", "홍합", "굴", "전복"}
    return {value} if value in SINGLE else None


def conflicts(queries, labels):
    """Return None if the evidence/query cannot be compared using the supported vocabulary."""
    query_terms = [terms(q) for q in queries]
    label_terms = [terms(label) for label in labels]
    if any(t is None for t in query_terms + label_terms):
        return None
    return any(q & label for q in query_terms for label in label_terms)
