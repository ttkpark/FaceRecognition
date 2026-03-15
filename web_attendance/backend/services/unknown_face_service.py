# services/unknown_face_service.py - 미등록 얼굴 C+B 방식
import base64
import json
import time
from typing import List, Optional, Tuple

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database import UnknownFaceModel, PersonModel, AsyncSessionLocal
from ml.face_embedding import cosine_similarity


async def save_unknown_face(
    db: AsyncSession,
    embedding: List[float],
    thumbnail_base64: str,
    first_seen: int,
    last_seen: int,
    frame_count: int,
) -> UnknownFaceModel:
    """미등록 얼굴 저장 (품질·연속 감지 통과 후)"""
    uf = UnknownFaceModel(
        embedding=json.dumps(embedding),
        thumbnail=thumbnail_base64,
        firstSeen=first_seen,
        lastSeen=last_seen,
        frameCount=frame_count,
        clusterId=None,
    )
    db.add(uf)
    await db.commit()
    await db.refresh(uf)
    return uf


async def get_all_unknown_faces(db: AsyncSession) -> List[UnknownFaceModel]:
    """미등록 얼굴 전체 조회 (클러스터링 전)"""
    r = await db.execute(
        select(UnknownFaceModel).order_by(UnknownFaceModel.firstSeen.desc())
    )
    return list(r.scalars().all())


def _cluster_embeddings(
    faces: List[Tuple[int, List[float]]],
    threshold: float,
) -> List[List[int]]:
    """Union-Find로 유사도 기반 클러스터링. threshold 이상이면 같은 사람."""
    n = len(faces)
    parent = list(range(n))

    def find(x: int) -> int:
        if parent[x] != x:
            parent[x] = find(parent[x])
        return parent[x]

    def union(x: int, y: int):
        px, py = find(x), find(y)
        if px != py:
            parent[px] = py

    for i in range(n):
        for j in range(i + 1, n):
            sim = cosine_similarity(faces[i][1], faces[j][1])
            if sim >= threshold:
                union(i, j)

    clusters: dict[int, List[int]] = {}
    for i in range(n):
        root = find(i)
        clusters.setdefault(root, []).append(faces[i][0])
    return list(clusters.values())


async def cluster_unknown_faces(db: AsyncSession) -> List[dict]:
    """
    미등록 얼굴을 클러스터링. 같은 사람끼리 묶음.
    반환: [{ clusterId, faceIds, thumbnail, count, firstSeen, lastSeen }, ...]
    """
    faces = await get_all_unknown_faces(db)
    if not faces:
        return []

    # clusterId 초기화
    for uf in faces:
        uf.clusterId = None
    await db.commit()

    id_to_face = {uf.id: uf for uf in faces}
    face_list = [(uf.id, uf.get_embedding()) for uf in faces]
    # 화면 클러스터링은 더 낮은 임계값 사용 → 크롭/각도 다른 같은 얼굴을 한 그룹으로
    clusters = _cluster_embeddings(
        face_list,
        settings.unknown_cluster_merge_threshold,
    )

    result = []
    for idx, face_ids in enumerate(clusters):
        ufs = [id_to_face[fid] for fid in face_ids]
        # 대표 썸네일: 가장 최근 것
        best = max(ufs, key=lambda u: u.lastSeen)
        result.append({
            "clusterId": idx,
            "faceIds": face_ids,
            "thumbnail": best.thumbnail,
            "count": sum(u.frameCount for u in ufs),
            "firstSeen": min(u.firstSeen for u in ufs),
            "lastSeen": max(u.lastSeen for u in ufs),
        })
        # DB에 clusterId 저장
        for uf in ufs:
            uf.clusterId = idx
    await db.commit()
    return result


async def register_cluster(
    db: AsyncSession,
    cluster_id: int,
    name: str,
) -> PersonModel:
    """
    클러스터를 Person으로 등록. 해당 클러스터의 모든 임베딩을 합쳐서 등록.
    """
    r = await db.execute(
        select(UnknownFaceModel).where(UnknownFaceModel.clusterId == cluster_id)
    )
    ufs = r.scalars().all()
    if not ufs:
        raise ValueError(f"클러스터 {cluster_id}에 해당하는 얼굴이 없습니다.")

    all_embeddings = []
    for uf in ufs:
        all_embeddings.append(uf.get_embedding())

    person = PersonModel(
        name=name.strip(),
        embedding=json.dumps(all_embeddings),
    )
    db.add(person)
    await db.commit()
    await db.refresh(person)

    # unknown_faces에서 삭제
    from sqlalchemy import delete
    await db.execute(delete(UnknownFaceModel).where(UnknownFaceModel.clusterId == cluster_id))
    await db.commit()

    return person


async def delete_unknown_face(db: AsyncSession, face_id: int) -> bool:
    """미등록 얼굴 단건 삭제 (등록 거부 시)"""
    from sqlalchemy import delete
    r = await db.execute(delete(UnknownFaceModel).where(UnknownFaceModel.id == face_id))
    await db.commit()
    return r.rowcount > 0


async def delete_cluster(db: AsyncSession, cluster_id: int) -> int:
    """클러스터 전체 삭제 (등록 거부 시)"""
    from sqlalchemy import delete
    r = await db.execute(delete(UnknownFaceModel).where(UnknownFaceModel.clusterId == cluster_id))
    await db.commit()
    return r.rowcount or 0
