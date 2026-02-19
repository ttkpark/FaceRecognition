# ml/face_matcher.py
from typing import List, Optional, Tuple
from ml.face_embedding import cosine_similarity


def find_best_match(
    current_embedding: List[float],
    persons_with_embeddings: List[Tuple[int, str, List[List[float]]]],
    threshold: float,
) -> Optional[Tuple[int, str, float]]:
    best_id, best_name, best_sim = None, None, -1.0
    for pid, name, embeddings in persons_with_embeddings:
        for emb in embeddings:
            sim = cosine_similarity(current_embedding, emb)
            if sim > best_sim:
                best_sim, best_id, best_name = sim, pid, name
    return (best_id, best_name, best_sim) if best_sim >= threshold else None
