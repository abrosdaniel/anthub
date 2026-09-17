package dev.abros.anthub.core;
/** Stable machine code and a safe, actionable player-facing message. */
public final class CommunityFailure extends IllegalArgumentException {
 public enum Code { CONFLICT, INVALID, FORBIDDEN, NOT_FOUND, EXPIRED, UNAVAILABLE }
 private final Code code;
 public CommunityFailure(Code code,String message){super(message);this.code=code;}
 public Code code(){return code;}
}
