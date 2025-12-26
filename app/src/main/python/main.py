import os
import yt_dlp


# -------------------------------------------------
# 유틸리티 함수
# -------------------------------------------------
def safe_title(info: dict, max_len: int = 80) -> str:
    """
    영상 제목에서 파일 시스템에 저장할 때 문제가 될 수 있는 특수문자를 제거합니다.
    윈도우 및 안드로이드 파일 시스템 호환성을 위해 사용합니다.
    """
    title = info.get("title", "video")
    # 파일명으로 사용할 수 없는 문자들을 언더바(_)로 치환
    for ch in ['/', '\\', ':', '*', '?', '"', '<', '>', '|']:
        title = title.replace(ch, '_')
    return title.strip()[:max_len]


def _unique_outtmpl(work_dir: str, kind: str, target_h: int, target_fps: int) -> str:
    """
    yt-dlp가 사용할 고유한 출력 파일명 템플릿을 생성합니다.
    파일명에 해상도, FPS, 파일 종류(kind)를 포함하여 중복을 방지합니다.
    """
    return os.path.join(
        work_dir,
        f"%(title).80s__%(id)s__H{int(target_h)}_F{int(target_fps)}_{kind}.%(ext)s"
    )


def _num(x, default=None):
    """
    문자열 등을 실수형(float)으로 변환하며, 실패 시 기본값을 반환합니다.
    """
    try:
        return float(x)
    except Exception:
        return default


# -------------------------------------------------
# FPS(초당 프레임) 관련 헬퍼 함수
# -------------------------------------------------
def _fps_value(fmt: dict):
    """
    포맷 정보 딕셔너리에서 FPS 값을 추출합니다.
    """
    v = fmt.get("fps")
    if v is None:
        return None
    try:
        return float(v)
    except Exception:
        return None


def _fps_le_ok(fmt: dict, target_fps: int) -> bool:
    """
    해당 포맷의 FPS가 목표 FPS(target_fps) 이하인지 확인합니다.
    None인 경우(FPS 정보 없음)에는 허용(True)으로 처리합니다.
    """
    fps = _fps_value(fmt)
    if fps is None:
        return True
    return fps <= float(target_fps)


# -------------------------------------------------
# Muxer 지원 여부 확인 (비디오 전용 스트림)
# -------------------------------------------------
def _is_muxer_supported_video_only(f: dict) -> bool:
    """
    안드로이드의 기본 MediaMuxer가 합칠 수 있는 비디오 포맷인지 확인합니다.
    앱 내에 FFmpeg 바이너리가 없으므로, 네이티브 Muxer가 지원하는 코덱만 선별해야 합니다.

    조건:
    1. 오디오가 없어야 함 (video only)
    2. 비디오 코덱이 존재해야 함
    3. 확장자가 mp4여야 함
    4. 코덱이 H.264(AVC) 또는 H.265(HEVC) 계열이어야 함
    """
    if f.get("acodec") != "none":
        return False
    if f.get("vcodec") in (None, "none"):
        return False
    if not f.get("height"):
        return False
    if (f.get("ext") or "").lower() != "mp4":
        return False

    vcodec = (f.get("vcodec") or "").lower()
    # H.264 (AVC) 확인
    if vcodec.startswith("avc") or "h264" in vcodec:
        return True
    # H.265 (HEVC) 확인
    if vcodec.startswith("hvc") or vcodec.startswith("hev") or "hevc" in vcodec:
        return True
    return False


# -------------------------------------------------
# 포맷 선택 정책 헬퍼 함수
# -------------------------------------------------
def _exists_height_target(formats, target_h: int, kind: str) -> bool:
    """
    주어진 포맷 목록 중에 목표 해상도(target_h) 이상인 포맷이 존재하는지 확인합니다.
    kind가 'progressive'면 오디오/비디오가 합쳐진 포맷만,
    그 외에는 안드로이드 Muxer가 지원하는 비디오 전용 포맷만 검사합니다.
    """
    for f in formats:
        h = f.get("height")
        if not h or int(h) < int(target_h):
            continue
        if kind == "progressive":
            if f.get("vcodec") in (None, "none") or f.get("acodec") in (None, "none"):
                continue
            return True
        else:
            if _is_muxer_supported_video_only(f):
                return True
    return False


def _best_height_for_kind(formats, kind: str) -> int | None:
    """
    주어진 종류(kind)에서 가장 높은 해상도 값을 찾습니다.
    """
    best = None
    for f in formats:
        h = f.get("height")
        if not h:
            continue
        h = int(h)
        if kind == "progressive":
            if f.get("vcodec") in (None, "none") or f.get("acodec") in (None, "none"):
                continue
        else:
            if not _is_muxer_supported_video_only(f):
                continue
        if best is None or h > best:
            best = h
    return best


def _exists_fps_le_in_target_region(formats, target_h: int, target_fps: int, kind: str) -> bool:
    """
    목표 해상도 이상이면서, 목표 FPS 조건을 만족하는 포맷이 있는지 확인합니다.
    """
    for f in formats:
        h = f.get("height")
        if not h or int(h) < int(target_h):
            continue

        if kind == "progressive":
            if f.get("vcodec") in (None, "none") or f.get("acodec") in (None, "none"):
                continue
        else:
            if not _is_muxer_supported_video_only(f):
                continue

        if _fps_le_ok(f, target_fps):
            return True
    return False


def _exists_fps_le_at_best_height(formats, target_fps: int, kind: str) -> bool:
    """
    가장 높은 해상도(Best Height)를 가진 포맷들 중에서, 목표 FPS 조건을 만족하는 것이 있는지 확인합니다.
    """
    bh = _best_height_for_kind(formats, kind)
    if bh is None:
        return False

    for f in formats:
        h = f.get("height")
        if not h or int(h) != int(bh):
            continue

        if kind == "progressive":
            if f.get("vcodec") in (None, "none") or f.get("acodec") in (None, "none"):
                continue
        else:
            if not _is_muxer_supported_video_only(f):
                continue

        if _fps_le_ok(f, target_fps):
            return True
    return False


# -------------------------------------------------
# 포맷 선택기 (Picker)
# -------------------------------------------------
def _pick_one(formats, kind: str, target_h: int, target_fps: int, height_mode: str, fps_mode: str):
    """
    조건에 맞는 포맷 후보들을 필터링하고 정렬하여 가장 적합한 하나를 선택합니다.

    정렬 우선순위:
    1. 해상도 (TARGET 모드면 오름차순, BEST 모드면 내림차순)
    2. FPS (높은 순)
    3. 확장자 (mp4 선호)
    4. 비트레이트 (높은 순)
    """
    cands = []
    for f in formats:
        h = f.get("height")
        if not h:
            continue
        h = int(h)

        # 포맷 종류에 따른 1차 필터링
        if kind == "progressive":
            if f.get("vcodec") in (None, "none") or f.get("acodec") in (None, "none"):
                continue
        else:
            if not _is_muxer_supported_video_only(f):
                continue

        # 해상도 모드에 따른 필터링 (TARGET인 경우 목표치 미만 제외)
        if height_mode == "TARGET" and h < int(target_h):
            continue

        # FPS 모드에 따른 필터링 (TARGET인 경우 목표치 초과 제외)
        if fps_mode == "TARGET" and not _fps_le_ok(f, target_fps):
            continue

        cands.append(f)

    if not cands:
        return None

    def fps_key(fmt):
        fps = _fps_value(fmt)
        if fps is None:
            return -1.0
        return float(fps)

    def ext_pref(fmt):
        return 1 if (fmt.get("ext") or "").lower() == "mp4" else 0

    def bitrate(fmt):
        return _num(fmt.get("tbr"), 0.0) or 0.0

    # 정렬 수행
    if height_mode == "TARGET":
        # 목표 해상도에 근접한 것부터 (오름차순)
        cands.sort(
            key=lambda f: (
                int(f.get("height") or 0),
                -fps_key(f),
                -ext_pref(f),
                -bitrate(f),
            )
        )
    else:
        # 가능한 최고 화질 (내림차순)
        cands.sort(
            key=lambda f: (
                -int(f.get("height") or 0),
                -fps_key(f),
                -ext_pref(f),
                -bitrate(f),
            )
        )

    return cands[0]


# -------------------------------------------------
# 메인 실행 함수
# -------------------------------------------------
def run_ytdlp(
        url,
        mode,
        work_dir,
        callback=None,
        audio_quality="high",
        video_resolution=1080,
        video_fps=60,
        ffmpeg_path=None,
):
    """
    Kotlin/Java에서 호출하는 진입점 함수입니다.

    매개변수:
    - url: 유튜브 영상 URL
    - mode: 0이면 오디오 다운로드, 1이면 비디오 다운로드
    - work_dir: 임시 다운로드 경로
    - callback: 진행률 업데이트를 위한 콜백 인터페이스 (Java 객체)
    - audio_quality: 오디오 품질 설정 (low, mid, high)
    - video_resolution: 목표 비디오 해상도 (예: 1080)
    - video_fps: 목표 비디오 FPS (예: 60)

    반환값:
    - 성공 시: "OK_AUDIO" 또는 "OK_SINGLE", "OK_SPLIT"으로 시작하는 문자열 (파일 경로 포함)
    - 실패 시: "ERR:"로 시작하는 에러 메시지
    """
    try:
        os.makedirs(work_dir, exist_ok=True)
        last_percent = -1

        # 진행률 업데이트 훅
        def hook(d):
            nonlocal last_percent
            if callback is None:
                return
            if not hasattr(callback, "onProgress"):
                return
            if d.get("status") == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes") or 0
                if total > 0:
                    p = int(downloaded * 100 / total)
                    # 1% 단위로만 콜백 호출하여 부하 감소
                    if p != last_percent:
                        last_percent = p
                        callback.onProgress(p)

        common_opts = {
            "quiet": True,
            "progress_hooks": [hook],
            "postprocessors": [],
            "overwrites": True,
            "continuedl": False,
            "nopart": True,
            "cachedir": False,
        }

        target_h = int(video_resolution)
        target_fps = int(video_fps)

        # ---------------- 오디오 품질 설정 ----------------
        if audio_quality == "low":
            # 낮은 품질: 64kbps 이하
            audio_fmt_music = "bestaudio[ext=m4a][abr<=64]/bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a][abr<=64]/bestaudio[ext=m4a]"
        elif audio_quality == "mid":
            # 중간 품질: 128kbps 이하
            audio_fmt_music = "bestaudio[ext=m4a][abr<=128]/bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a][abr<=128]/bestaudio[ext=m4a]"
        else:
            # 높은 품질: 제한 없음
            audio_fmt_music = "bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a]"

        # 모드 0: 음악(오디오) 다운로드
        if mode == 0:
            outtmpl = _unique_outtmpl(work_dir, "music", 0, target_fps)
            with yt_dlp.YoutubeDL({
                **common_opts,
                "outtmpl": outtmpl,
                "format": audio_fmt_music,
            }) as ydl:
                info = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(info)

            return (
                "OK_AUDIO\n"
                f"FILE={path}\n"
                f"FINAL_TITLE={safe_title(info)}"
            )

        # ---------------- 메타데이터 추출 (비디오 모드) ----------------
        # 실제 다운로드 전에 포맷 정보를 먼저 가져옵니다.
        with yt_dlp.YoutubeDL({"quiet": True, "cachedir": False}) as ydl:
            info0 = ydl.extract_info(url, download=False)

        formats = info0.get("formats", [])
        final_title = safe_title(info0)

        # M4A 포맷의 오디오가 존재하는지 확인
        has_m4a_audio_only = any(
            f.get("vcodec") == "none"
            and (f.get("ext") == "m4a" or (f.get("acodec") or "").startswith("mp4a"))
            for f in formats
        )

        # ---------------- 다운로드 정책 결정 ----------------
        # 목표 해상도를 만족하는 프로그레시브(단일 파일) 혹은 비디오 전용 포맷이 있는지 확인
        has_h_prog = _exists_height_target(formats, target_h, "progressive")
        has_h_vo = _exists_height_target(formats, target_h, "video_only") if has_m4a_audio_only else False
        has_height_any = has_h_prog or has_h_vo

        # 원하는 해상도가 있으면 TARGET 모드, 없으면 가능한 최고 화질(BEST) 모드
        height_mode = "TARGET" if has_height_any else "BEST"

        if height_mode == "TARGET":
            has_fps_prog = _exists_fps_le_in_target_region(formats, target_h, target_fps, "progressive")
            has_fps_vo = _exists_fps_le_in_target_region(formats, target_h, target_fps, "video_only") if has_m4a_audio_only else False
            has_fps_any = has_fps_prog or has_fps_vo
        else:
            has_fps_prog = _exists_fps_le_at_best_height(formats, target_fps, "progressive")
            has_fps_vo = _exists_fps_le_at_best_height(formats, target_fps, "video_only") if has_m4a_audio_only else False
            has_fps_any = has_fps_prog or has_fps_vo

        fps_mode = "TARGET" if has_fps_any else "BEST"

        # ---------------- 1) 프로그레시브 (단일 파일) 시도 ----------------
        # 오디오/비디오가 합쳐진 파일이 조건에 맞으면 우선 다운로드 (합치는 과정 불필요)
        chosen_prog = _pick_one(formats, "progressive", target_h, target_fps, height_mode, fps_mode)
        if chosen_prog is not None:
            outtmpl = _unique_outtmpl(work_dir, "single", target_h, target_fps)
            with yt_dlp.YoutubeDL({
                **common_opts,
                "outtmpl": outtmpl,
                "format": chosen_prog["format_id"],
            }) as ydl:
                sinfo = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(sinfo)

            return (
                "OK_SINGLE\n"
                f"FILE={path}\n"
                f"FINAL_TITLE={final_title}"
            )

        # ---------------- 2) 분할 다운로드 (비디오+오디오) ----------------
        # 프로그레시브가 없거나 조건에 안 맞으면, 비디오와 오디오를 따로 받아 앱에서 합칩니다.
        # 단, 안드로이드 Muxer가 지원하는 비디오 포맷이어야 합니다.
        if has_m4a_audio_only:
            chosen_vo = _pick_one(formats, "video_only", target_h, target_fps, height_mode, fps_mode)
            if chosen_vo is not None:
                video_tmpl = _unique_outtmpl(work_dir, "video", target_h, target_fps)
                audio_tmpl = _unique_outtmpl(work_dir, "audio", target_h, target_fps)

                # 비디오 다운로드
                with yt_dlp.YoutubeDL({
                    **common_opts,
                    "outtmpl": video_tmpl,
                    "format": chosen_vo["format_id"],
                }) as ydl:
                    vinfo = ydl.extract_info(url, download=True)
                    video_path = ydl.prepare_filename(vinfo)

                # 오디오 다운로드
                with yt_dlp.YoutubeDL({
                    **common_opts,
                    "outtmpl": audio_tmpl,
                    "format": audio_fmt_video,
                }) as ydl:
                    ainfo = ydl.extract_info(url, download=True)
                    audio_path = ydl.prepare_filename(ainfo)

                # 앱(Kotlin) 측에 두 파일 경로를 전달하여 병합 요청
                return (
                    "OK_SPLIT\n"
                    f"VIDEO={video_path}\n"
                    f"AUDIO={audio_path}\n"
                    f"FINAL_TITLE={final_title}"
                )

        # ---------------- 3) 최후의 수단 (Best Fallback) ----------------
        # 위 조건들을 모두 만족하지 못하면 yt-dlp 기본 'best' 포맷으로 다운로드
        outtmpl = _unique_outtmpl(work_dir, "best", target_h, target_fps)
        with yt_dlp.YoutubeDL({
            **common_opts,
            "outtmpl": outtmpl,
            "format": "best",
        }) as ydl:
            sinfo = ydl.extract_info(url, download=True)
            path = ydl.prepare_filename(sinfo)

        return (
            "OK_SINGLE\n"
            f"FILE={path}\n"
            f"FINAL_TITLE={final_title}"
        )

    except Exception as e:
        # 에러 발생 시 예외 이름과 메시지 반환
        return f"ERR: {type(e).__name__}\n{e}"