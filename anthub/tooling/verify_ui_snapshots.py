"""Fail CI on missing/invalid frames or failed native layout/contrast assertions.
Screenshots are uploaded for review; no cross-GPU exact pixel equality is assumed.
"""
import pathlib, struct, sys, zlib

def validate(directory, count, log, marker):
    text=pathlib.Path(log).read_text(encoding="utf-8", errors="replace")
    if marker not in text or "_UI_FAILED" in text or "ANTHUB_NATIVE_FAILED" in text or "Error executing task on Client" in text:
        raise ValueError("Native UI regression checks did not complete")
    frames=sorted(pathlib.Path(directory).glob("*.png"))
    if len(frames)!=int(count):
        raise ValueError(f"Expected {count} frames, got {len(frames)}")
    for path in frames:
        data=path.read_bytes()
        if data[:8]!=b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"Invalid screenshot: {path}")
        width,height=struct.unpack(">II",data[16:24])
        if width<320 or height<200:
            raise ValueError(f"Unexpected viewport: {path}: {width}x{height}")
        offset=8; pixels=bytearray()
        while offset<len(data):
            length=struct.unpack(">I",data[offset:offset+4])[0]
            kind=data[offset+4:offset+8];body=data[offset+8:offset+8+length]
            crc=struct.unpack(">I",data[offset+8+length:offset+12+length])[0]
            if zlib.crc32(kind+body)&0xffffffff!=crc:
                raise ValueError(f"Broken PNG chunk: {path}")
            if kind==b"IDAT":pixels.extend(body)
            offset+=12+length
        if len(zlib.decompress(pixels))<width*height:
            raise ValueError(f"Incomplete rendered frame: {path}")
    print(f"Verified {len(frames)} native UI screenshots")

if __name__=="__main__":
    validate(*sys.argv[1:])
