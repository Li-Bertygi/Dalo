import os
import yt_dlp


# -------------------------------------------------
# Utils
# -------------------------------------------------
def safe_title(info: dict, max_len: int = 80) -> str:
    title = info.get("title", "video")
    for ch in ['/', '\\', ':', '*', '?', '"', '<', '>', '|']:
        title = title.replace(ch, '_')
    return title.strip()[:max_len]


def _unique_outtmpl(work_dir: str, kind: str, target_h: int, target_fps: int) -> str:
    return os.path.join(
        work_dir,
        f"%(title).80s__%(id)s__H{int(target_h)}_F{int(target_fps)}_{kind}.%(ext)s"
    )


def _num(x, default=None):
    try:
        return float(x)
    except Exception:
        return default


# -------------------------------------------------
# FPS helpers
# -------------------------------------------------
def _fps_value(fmt: dict):
    v = fmt.get("fps")
    if v is None:
        return None
    try:
        return float(v)
    except Exception:
        return None


def _fps_le_ok(fmt: dict, target_fps: int) -> bool:
    fps = _fps_value(fmt)
    if fps is None:
        return True
    return fps <= float(target_fps)


# -------------------------------------------------
# Muxer supported video-only (MediaMuxer 안전)
# -------------------------------------------------
def _is_muxer_supported_video_only(f: dict) -> bool:
    if f.get("acodec") != "none":
        return False
    if f.get("vcodec") in (None, "none"):
        return False
    if not f.get("height"):
        return False
    if (f.get("ext") or "").lower() != "mp4":
        return False

    vcodec = (f.get("vcodec") or "").lower()
    if vcodec.startswith("avc") or "h264" in vcodec:
        return True
    if vcodec.startswith("hvc") or vcodec.startswith("hev") or "hevc" in vcodec:
        return True
    return False


# -------------------------------------------------
# Policy helpers
# -------------------------------------------------
def _exists_height_target(formats, target_h: int, kind: str) -> bool:
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
# Picker
# -------------------------------------------------
def _pick_one(formats, kind: str, target_h: int, target_fps: int, height_mode: str, fps_mode: str):
    cands = []
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

        if height_mode == "TARGET" and h < int(target_h):
            continue

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

    if height_mode == "TARGET":
        cands.sort(
            key=lambda f: (
                int(f.get("height") or 0),
                -fps_key(f),
                -ext_pref(f),
                -bitrate(f),
            )
        )
    else:
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
# Entry
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
    try:
        os.makedirs(work_dir, exist_ok=True)
        last_percent = -1

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

        # ---------------- AUDIO ----------------
        if audio_quality == "low":
            audio_fmt_music = "bestaudio[ext=m4a][abr<=64]/bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a][abr<=64]/bestaudio[ext=m4a]"
        elif audio_quality == "mid":
            audio_fmt_music = "bestaudio[ext=m4a][abr<=128]/bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a][abr<=128]/bestaudio[ext=m4a]"
        else:
            audio_fmt_music = "bestaudio[ext=m4a]"
            audio_fmt_video = "bestaudio[ext=m4a]"

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

        # ---------------- META ----------------
        with yt_dlp.YoutubeDL({"quiet": True, "cachedir": False}) as ydl:
            info0 = ydl.extract_info(url, download=False)

        formats = info0.get("formats", [])
        final_title = safe_title(info0)

        has_m4a_audio_only = any(
            f.get("vcodec") == "none"
            and (f.get("ext") == "m4a" or (f.get("acodec") or "").startswith("mp4a"))
            for f in formats
        )

        # ---------------- POLICY ----------------
        has_h_prog = _exists_height_target(formats, target_h, "progressive")
        has_h_vo = _exists_height_target(formats, target_h, "video_only") if has_m4a_audio_only else False
        has_height_any = has_h_prog or has_h_vo

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

        # ---------------- 1) Progressive ----------------
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

        # ---------------- 2) Split(Mux) ----------------
        if has_m4a_audio_only:
            chosen_vo = _pick_one(formats, "video_only", target_h, target_fps, height_mode, fps_mode)
            if chosen_vo is not None:
                video_tmpl = _unique_outtmpl(work_dir, "video", target_h, target_fps)
                audio_tmpl = _unique_outtmpl(work_dir, "audio", target_h, target_fps)

                with yt_dlp.YoutubeDL({
                    **common_opts,
                    "outtmpl": video_tmpl,
                    "format": chosen_vo["format_id"],
                }) as ydl:
                    vinfo = ydl.extract_info(url, download=True)
                    video_path = ydl.prepare_filename(vinfo)

                with yt_dlp.YoutubeDL({
                    **common_opts,
                    "outtmpl": audio_tmpl,
                    "format": audio_fmt_video,
                }) as ydl:
                    ainfo = ydl.extract_info(url, download=True)
                    audio_path = ydl.prepare_filename(ainfo)

                return (
                    "OK_SPLIT\n"
                    f"VIDEO={video_path}\n"
                    f"AUDIO={audio_path}\n"
                    f"FINAL_TITLE={final_title}"
                )

        # ---------------- 3) Best fallback ----------------
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
        return f"ERR: {type(e).__name__}\n{e}"
