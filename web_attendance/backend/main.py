# main.py - FastAPI 웹 서버 (Windows, ML 처리)
import base64
import json
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import List, Set

from fastapi import FastAPI, Depends, HTTPException, UploadFile, File, Form, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database import init_db, get_db, PersonModel, AttendanceModel, AsyncSessionLocal
from services.face_service import (
    image_bytes_to_bgr,
    detect_and_embed_all,
    recognize_and_match,
    is_ml_ready,
    get_unknown_face_candidate,
)
from services.unknown_face_service import (
    save_unknown_face,
    get_all_unknown_faces,
    cluster_unknown_faces,
    register_cluster,
    delete_cluster,
)
from ml.face_embedding import cosine_similarity


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield
    # shutdown


app = FastAPI(title="Face Recognition Attendance API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 정적 파일 (웹 관리 화면)
STATIC_DIR = Path(__file__).resolve().parent / "static"
if STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")


# ---------- WebSocket (실시간 스트리밍) ----------
class ConnectionManager:
    def __init__(self):
        self.connections: Set[WebSocket] = set()

    async def connect(self, ws: WebSocket):
        await ws.accept()
        self.connections.add(ws)
        print(f"[WebSocket] 연결됨. 총 연결 수: {len(self.connections)}")

    def disconnect(self, ws: WebSocket):
        self.connections.discard(ws)
        print(f"[WebSocket] 연결 끊김. 총 연결 수: {len(self.connections)}")

    async def broadcast_frame(self, data: dict, exclude: WebSocket = None):
        """모든 연결에 전송 (exclude 제외, Flutter 발신자는 제외)"""
        dead = []
        for c in self.connections:
            if c is exclude:
                continue
            try:
                await c.send_json(data)
            except Exception:
                dead.append(c)
        for c in dead:
            self.connections.discard(c)


ws_manager = ConnectionManager()


def _encode_crop_to_base64(crop) -> str:
    import cv2
    _, buf = cv2.imencode(".jpg", crop)
    return base64.b64encode(buf.tobytes()).decode("ascii")


@app.websocket("/ws/stream")
async def websocket_stream(websocket: WebSocket):
    """Flutter에서 이미지 수신 → 웹 뷰어들에게 브로드캐스트. 인식 결과 포함."""
    await ws_manager.connect(websocket)
    # 미등록 얼굴 연속 감지 버퍼: [{emb, thumb, first_ts, last_ts, count}, ...]
    unknown_buffer: List[dict] = []
    try:
        while True:
            try:
                data = await websocket.receive()
            except RuntimeError as e:
                # 웹 페이지 연결이 끊어졌거나 수신 불가능한 상태
                if "disconnect" in str(e).lower():
                    break
                raise
            if "bytes" in data:
                img_bytes = data["bytes"]
            elif "text" in data:
                # JSON 제어 메시지 (예: {"type":"ping"})
                msg = json.loads(data["text"])
                if msg.get("type") == "ping":
                    await websocket.send_json({"type": "pong"})
                continue
            else:
                continue
            img = image_bytes_to_bgr(img_bytes)
            if img is None:
                continue
            # 얼굴 인식 (ML 로드 시)
            recognition = None
            if is_ml_ready():
                from database import AsyncSessionLocal
                async with AsyncSessionLocal() as db:
                    r = await db.execute(select(PersonModel))
                    persons = r.scalars().all()
                    persons_data = [(p.id, p.name, p.get_embeddings()) for p in persons]
                match = recognize_and_match(img, persons_data)
                today = time.strftime("%Y-%m-%d", time.localtime())
                ts = int(time.time() * 1000)
                if match:
                    person_id, person_name, similarity = match
                    async with AsyncSessionLocal() as db:
                        r2 = await db.execute(
                            select(AttendanceModel).where(
                                and_(AttendanceModel.personId == person_id, AttendanceModel.date == today)
                            )
                        )
                        existing = r2.scalar_one_or_none()
                        attendance_recorded = False
                        if not existing:
                            att = AttendanceModel(personId=person_id, timestamp=ts, date=today)
                            db.add(att)
                            await db.commit()
                            attendance_recorded = True
                    recognition = {
                        "matched": True,
                        "person": {"id": person_id, "name": person_name},
                        "similarity": round(similarity, 4),
                        "attendanceRecorded": attendance_recorded,
                    }
                else:
                    recognition = {"matched": False}
                    # 미등록 얼굴: 연속 N프레임 시 DB 저장 (C+B 방식)
                    if is_ml_ready():
                        cand = get_unknown_face_candidate(
                            img, settings.unknown_min_face_size
                        )
                        if cand:
                            emb, crop = cand
                            thumb_b64 = _encode_crop_to_base64(crop)
                            ts = int(time.time() * 1000)
                            # 버퍼에서 유사한 얼굴 찾기 (0.88 이상 = 같은 사람)
                            best_idx = None
                            best_sim = 0.0
                            for i, entry in enumerate(unknown_buffer):
                                sim = cosine_similarity(emb, entry["emb"])
                                if sim >= settings.cluster_similarity_threshold and sim > best_sim:
                                    best_sim, best_idx = sim, i
                            if best_idx is not None:
                                unknown_buffer[best_idx]["last_ts"] = ts
                                unknown_buffer[best_idx]["count"] += 1
                                unknown_buffer[best_idx]["thumb"] = thumb_b64
                                if unknown_buffer[best_idx]["count"] >= settings.unknown_consecutive_frames:
                                    entry = unknown_buffer.pop(best_idx)
                                    async with AsyncSessionLocal() as db:
                                        await save_unknown_face(
                                            db, entry["emb"], entry["thumb"],
                                            entry["first_ts"], entry["last_ts"], entry["count"]
                                        )
                                    print(f"[Unknown] 미등록 얼굴 저장 (연속 {entry['count']}프레임)")
                            else:
                                unknown_buffer.append({
                                    "emb": emb, "thumb": thumb_b64,
                                    "first_ts": ts, "last_ts": ts, "count": 1,
                                })
                            # 30초 이상 된 항목 제거
                            cutoff = ts - 30000
                            unknown_buffer[:] = [e for e in unknown_buffer if e["last_ts"] > cutoff]
            else:
                recognition = {"matched": False}  # ML 미로드 시
            b64 = base64.b64encode(img_bytes).decode("ascii")
            broadcast_count = len(ws_manager.connections) - 1  # exclude self
            print(f"[Stream] 프레임 수신 및 브로드캐스트 (수신자: {broadcast_count}명)")
            await ws_manager.broadcast_frame(
                {"type": "frame", "image": b64, "recognition": recognition},
                exclude=websocket,
            )
            # Flutter 발신자에게 인식 결과만 전송 (UI 표시용)
            if recognition is not None:
                try:
                    await websocket.send_json({"type": "recognition", "recognition": recognition})
                except Exception:
                    pass
    except WebSocketDisconnect:
        pass
    finally:
        ws_manager.disconnect(websocket)


# ---------- API ----------

@app.get("/api/health")
async def health():
    return {
        "status": "ok",
        "ml_model_loaded": is_ml_ready(),
    }


@app.get("/api/config")
async def config():
    return {
        "similarityThreshold": settings.similarity_threshold,
        "recognitionIntervalMs": 500,
        "maxImagesPerRegister": settings.max_images_per_register,
    }


@app.get("/api/persons")
async def list_persons(db: AsyncSession = Depends(get_db)):
    r = await db.execute(select(PersonModel).order_by(PersonModel.id))
    rows = r.scalars().all()
    return {
        "persons": [
            {"id": p.id, "name": p.name, "createdAt": p.createdAt.isoformat() if p.createdAt else None}
            for p in rows
        ]
    }


@app.post("/api/register")
async def register_person(
    db: AsyncSession = Depends(get_db),
    name: str = Form(...),
    images: List[UploadFile] = File(..., description="최대 5장"),
):
    if not is_ml_ready():
        raise HTTPException(status_code=503, detail="ML 모델이 로드되지 않았습니다. models/mobilefacenet.tflite 를 넣어주세요.")
    if len(images) > settings.max_images_per_register:
        raise HTTPException(status_code=400, detail=f"이미지는 최대 {settings.max_images_per_register}장까지입니다.")
    all_embeddings = []
    for f in images:
        data = await f.read()
        img = image_bytes_to_bgr(data)
        if img is None:
            continue
        embs = detect_and_embed_all(img)
        for e in embs:
            all_embeddings.append(e)
    if not all_embeddings:
        raise HTTPException(status_code=400, detail="얼굴을 감지하지 못했습니다. 정면 얼굴 사진을 올려주세요.")
    import json
    person = PersonModel(name=name, embedding=json.dumps(all_embeddings))
    db.add(person)
    await db.commit()
    await db.refresh(person)
    return {"id": person.id, "name": person.name}


@app.delete("/api/persons/{person_id}")
async def delete_person(person_id: int, db: AsyncSession = Depends(get_db)):
    from sqlalchemy import delete
    await db.execute(delete(PersonModel).where(PersonModel.id == person_id))
    await db.commit()
    return JSONResponse(status_code=204, content=None)


# ---------- 미등록 얼굴 (C+B 방식) ----------
@app.get("/api/unknown-faces")
async def list_unknown_faces(db: AsyncSession = Depends(get_db)):
    """미등록 얼굴 목록 (클러스터링 전)"""
    faces = await get_all_unknown_faces(db)
    return {
        "unknownFaces": [
            {
                "id": f.id,
                "thumbnail": f.thumbnail,
                "firstSeen": f.firstSeen,
                "lastSeen": f.lastSeen,
                "frameCount": f.frameCount,
            }
            for f in faces
        ]
    }


@app.post("/api/unknown-faces/cluster")
async def cluster_unknown_faces_api(db: AsyncSession = Depends(get_db)):
    """미등록 얼굴 클러스터링 (같은 사람끼리 묶음)"""
    if not is_ml_ready():
        raise HTTPException(status_code=503, detail="ML 모델이 로드되지 않았습니다.")
    clusters = await cluster_unknown_faces(db)
    return {"clusters": clusters}


@app.post("/api/unknown-faces/register")
async def register_unknown_cluster(
    db: AsyncSession = Depends(get_db),
    clusterId: int = Form(...),
    name: str = Form(...),
):
    """클러스터에 이름 부여 → Person 등록"""
    if not name.strip():
        raise HTTPException(status_code=400, detail="이름을 입력하세요.")
    try:
        person = await register_cluster(db, clusterId, name.strip())
        return {"id": person.id, "name": person.name}
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@app.delete("/api/unknown-faces/cluster/{cluster_id}")
async def delete_unknown_cluster(
    cluster_id: int,
    db: AsyncSession = Depends(get_db),
):
    """클러스터 삭제 (등록 거부)"""
    n = await delete_cluster(db, cluster_id)
    return {"deleted": n}


# ---------- API ----------
@app.post("/api/recognize")
async def recognize(
    db: AsyncSession = Depends(get_db),
    image: UploadFile = File(...),
):
    if not is_ml_ready():
        raise HTTPException(status_code=503, detail="ML 모델이 로드되지 않았습니다.")
    data = await image.read()
    img = image_bytes_to_bgr(data)
    if img is None:
        raise HTTPException(status_code=400, detail="이미지를 읽을 수 없습니다.")
    r = await db.execute(select(PersonModel))
    persons = r.scalars().all()
    persons_data = [(p.id, p.name, p.get_embeddings()) for p in persons]
    match = recognize_and_match(img, persons_data)
    today = time.strftime("%Y-%m-%d", time.localtime())
    ts = int(time.time() * 1000)
    attendance_recorded = False
    if match:
        person_id, person_name, similarity = match
        # 오늘 이미 출석했는지 확인
        r2 = await db.execute(
            select(AttendanceModel).where(
                and_(AttendanceModel.personId == person_id, AttendanceModel.date == today)
            )
        )
        existing = r2.scalar_one_or_none()
        if not existing:
            att = AttendanceModel(personId=person_id, timestamp=ts, date=today)
            db.add(att)
            await db.commit()
            attendance_recorded = True
        return {
            "matched": True,
            "person": {"id": person_id, "name": person_name},
            "similarity": round(similarity, 4),
            "attendanceRecorded": attendance_recorded,
        }
    return {"matched": False, "attendanceRecorded": False}


def _attendance_to_dict(a: AttendanceModel, person_name: str = None):
    return {
        "id": a.id,
        "personId": a.personId,
        "personName": person_name,
        "timestamp": a.timestamp,
        "date": a.date,
    }


@app.get("/api/attendances")
async def list_attendances(
    db: AsyncSession = Depends(get_db),
    date: str | None = Query(None),
    personId: int | None = Query(None),
    from_date: str | None = Query(None, alias="from"),
    to_date: str | None = Query(None, alias="to"),
):
    q = select(AttendanceModel).order_by(AttendanceModel.timestamp.desc())
    if date:
        q = q.where(AttendanceModel.date == date)
    if personId is not None:
        q = q.where(AttendanceModel.personId == personId)
    if from_date:
        q = q.where(AttendanceModel.date >= from_date)
    if to_date:
        q = q.where(AttendanceModel.date <= to_date)
    r = await db.execute(q)
    rows = r.scalars().all()
    # load names
    person_ids = list({a.personId for a in rows})
    names = {}
    if person_ids:
        r2 = await db.execute(select(PersonModel).where(PersonModel.id.in_(person_ids)))
        for p in r2.scalars().all():
            names[p.id] = p.name
    return {
        "attendances": [_attendance_to_dict(a, names.get(a.personId)) for a in rows]
    }


@app.get("/api/attendances/check")
async def check_attendance(
    db: AsyncSession = Depends(get_db),
    personId: int = Query(...),
    date: str = Query(...),
):
    r = await db.execute(
        select(AttendanceModel).where(
            and_(AttendanceModel.personId == personId, AttendanceModel.date == date)
        )
    )
    rows = r.scalars().all()
    return {
        "date": date,
        "personId": personId,
        "attended": len(rows) > 0,
        "count": len(rows),
    }


@app.get("/api/attendances/export")
async def export_attendances(
    db: AsyncSession = Depends(get_db),
    from_date: str = Query(..., alias="from"),
    to_date: str = Query(..., alias="to"),
    format: str = Query("xlsx"),
):
    r = await db.execute(
        select(AttendanceModel).where(
            and_(
                AttendanceModel.date >= from_date,
                AttendanceModel.date <= to_date,
            )
        ).order_by(AttendanceModel.date, AttendanceModel.timestamp)
    )
    rows = r.scalars().all()
    person_ids = list({a.personId for a in rows})
    names = {}
    if person_ids:
        r2 = await db.execute(select(PersonModel).where(PersonModel.id.in_(person_ids)))
        for p in r2.scalars().all():
            names[p.id] = p.name
    if format == "csv":
        import io
        import csv
        buf = io.StringIO()
        w = csv.writer(buf)
        w.writerow(["날짜", "이름", "출석시각"])
        for a in rows:
            from datetime import datetime
            dt = datetime.utcfromtimestamp(a.timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")
            w.writerow([a.date, names.get(a.personId, ""), dt])
        return Response(
            content=buf.getvalue().encode("utf-8-sig"),
            media_type="text/csv; charset=utf-8",
            headers={"Content-Disposition": "attachment; filename=attendances.csv"},
        )
    # xlsx
    try:
        import openpyxl
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "출석"
        ws.append(["날짜", "이름", "출석시각"])
        for a in rows:
            from datetime import datetime
            dt = datetime.utcfromtimestamp(a.timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")
            ws.append([a.date, names.get(a.personId, ""), dt])
        out = Path(settings.database_url.replace("sqlite+aiosqlite:///", "")).parent / "export.xlsx"
        out = Path(__file__).resolve().parent / "export_temp.xlsx"
        wb.save(out)
        return FileResponse(out, filename="attendances.xlsx", media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# 웹 관리 화면
@app.get("/")
async def index():
    if (STATIC_DIR / "index.html").exists():
        return FileResponse(STATIC_DIR / "index.html")
    return {"message": "Face Recognition Attendance API. Use /api/health to check."}


@app.get("/stream")
async def stream_page():
    """실시간 스트림 뷰어 페이지"""
    if (STATIC_DIR / "stream.html").exists():
        return FileResponse(STATIC_DIR / "stream.html")
    raise HTTPException(status_code=404, detail="stream.html not found")


@app.get("/unknown")
async def unknown_faces_page():
    """미등록 얼굴 관리 페이지 (C+B 방식)"""
    if (STATIC_DIR / "unknown.html").exists():
        return FileResponse(STATIC_DIR / "unknown.html")
    raise HTTPException(status_code=404, detail="unknown.html not found")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=settings.host, port=settings.port)
